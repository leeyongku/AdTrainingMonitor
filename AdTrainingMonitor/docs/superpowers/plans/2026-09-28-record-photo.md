 # 기록 사진 첨부 기능 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자가 기록 입력 시 사진을 첨부해 서버 DB에 저장하고, 대원 본인과 관리자가 각각 그 사진을 열람할 수 있게 한다.

**Architecture:** 백엔드(`TrainingMonitor`, ASP.NET Core)는 `records` 테이블에 `photo bytea` 컬럼을 추가하고, 기존 JSON 기반 `POST /api/records`에 base64 사진 필드를 얹어 저장하며, 신규 `GET /api/records/{id}/photo`로 원본 바이트를 내려준다. 안드로이드(`AdTrainingMonitor`)는 기존 카메라 촬영 패턴(`FileProvider` + `TakePicture()`)을 기록 입력 화면에 재사용하고, 저장 직전에 리사이즈·압축·base64 인코딩한다. 열람은 기존 `PhotoViewActivity`를 로컬 Uri/원격 recordId 두 모드로 확장해 재사용하고, 관리자용으로는 비어있던 `RecordListViewFragment` 스캐폴드를 실제 화면으로 구현한다.

**Tech Stack:** ASP.NET Core 9 + EF Core(Npgsql), Kotlin + Retrofit/OkHttp + AndroidX Navigation/ViewBinding.

**Spec:** `docs/superpowers/specs/2026-09-28-record-photo-design.md`

## Global Constraints

- JSON 필드명은 서버가 snake_case로 주고받으므로, 안드로이드 DTO의 새 필드는 반드시 `@SerializedName`을 붙인다.
- 이 저장소에는 이 종류의 기능(컨트롤러/프래그먼트 배선)에 대한 자동화 테스트가 없다 (`CLAUDE.md` 규칙: 테스트가 없으면 최소 빌드/컴파일 성공을 확인). 각 태스크의 "테스트" 단계는 `dotnet build` / `./gradlew assembleDebug` 컴파일 확인 + (해당되는 경우) `adb`/`curl`을 이용한 수동 스모크 확인으로 구성한다 — 새 테스트 프레임워크를 도입하지 않는다.
- 기존 코드 스타일을 따른다: 주석은 한국어, KDoc은 함수 위에 한 줄~여러 줄, 기존 MVVM 패턴(Fragment는 View만, ViewModel이 통신) 유지.
- 백엔드 프로젝트 경로: `D:\LEE\TEST\TrainingMonitor` (이 저장소 밖, 별도 git 루트 `D:\LEE\TEST`). 안드로이드 프로젝트 경로: `D:\LEE\TEST\AdTrainingMonitor` (이 저장소).

---

## Task 1: `Record` 엔티티에 사진 컬럼 추가 + EF 마이그레이션

**Files:**
- Modify: `D:\LEE\TEST\TrainingMonitor\Models\Entities.cs` (Record 클래스, 153~184줄 부근)
- Create: `D:\LEE\TEST\TrainingMonitor\Migrations\<timestamp>_AddRecordPhoto.cs` (`dotnet ef`가 생성)

**Interfaces:**
- Produces: `TrainingMonitor.Models.Entities.Record.Photo` (`byte[]?`) — Task 2가 사용.

- [ ] **Step 1: `Record` 엔티티에 컬럼 추가**

`Models/Entities.cs`의 `Record` 클래스에서 `Note` 프로퍼티 바로 아래에 추가한다:

```csharp
    public string? Note { get; set; }

    // 촬영한 측정 증빙 사진 (선택, JPEG 바이너리 그대로 저장)
    public byte[]? Photo { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
```

(기존 `public string? Note { get; set; }`와 `public DateTime CreatedAt...` 사이에 `Photo` 줄만 끼워 넣는 것 — 나머지 줄은 그대로 둔다.)

- [ ] **Step 2: 마이그레이션 생성**

Run:
```
cd D:\LEE\TEST\TrainingMonitor
dotnet ef migrations add AddRecordPhoto
```

Expected: `Migrations/` 아래에 `<timestamp>_AddRecordPhoto.cs`가 생성되고, `Up()` 메서드 안에 `migrationBuilder.AddColumn<byte[]>(name: "photo", table: "records", type: "bytea", nullable: true);`가 포함된다. 생성된 파일을 열어 이 내용이 있는지 확인한다.

- [ ] **Step 3: 빌드 확인**

Run:
```
cd D:\LEE\TEST\TrainingMonitor
dotnet build
```

Expected: `Build succeeded.`

- [ ] **Step 4: Commit**

```bash
cd D:\LEE\TEST\TrainingMonitor
git add Models/Entities.cs Migrations/
git commit -m "feat: add Photo column to Record entity"
```

---

## Task 2: `POST /api/records` — 사진 저장 + `RecordDto`에 `HasPhoto` 추가

**Files:**
- Modify: `D:\LEE\TEST\TrainingMonitor\Controllers\RecordsController.cs`

**Interfaces:**
- Consumes: `Record.Photo` (Task 1).
- Produces: `RecordRequest.PhotoBase64` (string?), `RecordDto.HasPhoto` (bool) — Task 4(안드로이드 DTO)가 이 JSON 필드명(`photo_base64`, `has_photo`)과 맞춰야 한다.

- [ ] **Step 1: `RecordRequest`/`RecordDto` 레코드 정의 수정**

18~19번째 줄을 다음으로 교체:

```csharp
    public record RecordRequest(long? SessionId, long UserId, long CategoryId, double Value, string? Note, string? PhotoBase64);
    public record RecordDto(long Id, string UserName, string CategoryName, double Value, string Unit, string? Grade, DateOnly? MeasuredAt, bool HasPhoto);
```

- [ ] **Step 2: `CreateRecord`에 사진 디코딩/저장 로직 추가**

`CreateRecord` 메서드(24~60줄)를 다음으로 교체:

```csharp
    [HttpPost]
    [Authorize(Roles = "ADMIN")]
    public async Task<ActionResult<object>> CreateRecord([FromBody] RecordRequest req)
    {
        var user = await db.Users.FindAsync(req.UserId);
        var category = await db.Categories.FindAsync(req.CategoryId);
        if (user is null || category is null) return NotFound();

        var session = req.SessionId.HasValue
            ? await db.Sessions.FindAsync(req.SessionId.Value)
            : null;

        // 등급 자동 산출
        var grade = CalculateGrade(req.CategoryId, req.Value, user.Rank);

        // 동일 세션·종목 중복 방지
        if (req.SessionId.HasValue &&
            await db.Records.AnyAsync(r => r.SessionId == req.SessionId && r.UserId == req.UserId && r.CategoryId == req.CategoryId))
            return Conflict("이미 해당 세션에 같은 종목 기록이 있습니다.");

        byte[]? photo = null;
        if (req.PhotoBase64 is not null)
        {
            try
            {
                photo = Convert.FromBase64String(req.PhotoBase64);
            }
            catch (FormatException)
            {
                return BadRequest("잘못된 사진 데이터입니다.");
            }
        }

        var record = new Record
        {
            Session = session,
            UserId = req.UserId,
            User = user,
            CategoryId = req.CategoryId,
            Category = category,
            Value = req.Value,
            Grade = grade,
            Note = req.Note,
            Photo = photo
        };

        db.Records.Add(record);
        await db.SaveChangesAsync();

        return Ok(new { id = record.Id, grade });
    }
```

- [ ] **Step 3: `GetRecordDtos`에 `HasPhoto` 프로젝션 추가**

118~137줄의 `GetRecordDtos` 메서드에서 `.Select(...)` 블록을 다음으로 교체:

```csharp
            .Select(r => new RecordDto(
                r.Id,
                r.User.Name,
                r.Category.Name,
                r.Value,
                r.Category.Unit,
                r.Grade,
                r.Session != null ? r.Session.MeasuredAt : null,
                r.Photo != null
            ))
```

- [ ] **Step 4: 빌드 확인**

Run:
```
cd D:\LEE\TEST\TrainingMonitor
dotnet build
```

Expected: `Build succeeded.`

- [ ] **Step 5: Commit**

```bash
cd D:\LEE\TEST\TrainingMonitor
git add Controllers/RecordsController.cs
git commit -m "feat: accept and expose photo on record create/list"
```

---

## Task 3: `GET /api/records/{id}/photo` 엔드포인트

**Files:**
- Modify: `D:\LEE\TEST\TrainingMonitor\Controllers\RecordsController.cs`

**Interfaces:**
- Consumes: `Record.Photo`, `User.IsInRole`, `ClaimTypes.NameIdentifier` (컨트롤러에 이미 `using System.Security.Claims;` 있음).
- Produces: `GET api/records/{id}/photo` — Task 5(안드로이드 `ApiService.recordPhoto`)가 호출하는 엔드포인트.

- [ ] **Step 1: `GradeCriteria` 메서드 뒤에 사진 조회 메서드 추가**

`GradeCriteria` 메서드(84~95줄) 바로 다음에 추가:

```csharp
    // 기록에 첨부된 사진 원본을 내려준다. 본인 기록이거나 관리자만 조회 가능.
    [HttpGet("{id}/photo")]
    public async Task<IActionResult> GetPhoto(long id)
    {
        var record = await db.Records.FindAsync(id);
        if (record is null || record.Photo is null) return NotFound();

        if (!User.IsInRole("ADMIN"))
        {
            var requesterId = long.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
            if (record.UserId != requesterId) return Forbid();
        }

        return File(record.Photo, "image/jpeg");
    }
```

- [ ] **Step 2: 빌드 확인**

Run:
```
cd D:\LEE\TEST\TrainingMonitor
dotnet build
```

Expected: `Build succeeded.`

- [ ] **Step 3: 서버 실행 후 수동 스모크 확인**

서버를 실행한 상태(`dotnet run` 또는 기존 실행 중인 프로세스)에서, 사진이 없는 존재하지 않는 id로 호출했을 때 404가 오는지만 curl로 가볍게 확인한다(인증 토큰 없이도 401이 아니라 404/401 중 하나가 오는지 확인하는 수준이면 충분 — 실제 사진 유무 검증은 Task 6~7 완료 후 전체 시나리오에서 한다):

```
curl -i http://localhost:5133/api/records/999999/photo
```

Expected: `401 Unauthorized` (토큰 없이 호출했으므로) — 500 에러가 아니어야 한다.

- [ ] **Step 4: Commit**

```bash
cd D:\LEE\TEST\TrainingMonitor
git add Controllers/RecordsController.cs
git commit -m "feat: add record photo download endpoint"
```

---

## Task 4: 안드로이드 DTO/`ApiService` 확장

**Files:**
- Modify: `D:\LEE\TEST\AdTrainingMonitor\app\src\main\java\com\training\monitor\data\model\Dtos.kt`
- Modify: `D:\LEE\TEST\AdTrainingMonitor\app\src\main\java\com\training\monitor\data\api\ApiService.kt`

**Interfaces:**
- Consumes: 서버 JSON 필드 `photo_base64`(요청), `has_photo`(응답) — Task 2와 이름이 일치해야 함.
- Produces: `RecordDto.hasPhoto: Boolean`, `RecordRequest.photoBase64: String?`, `ApiService.recordPhoto(id): Response<ResponseBody>` — Task 5, 6, 7이 사용.

- [ ] **Step 1: `RecordDto`에 `hasPhoto` 추가**

`Dtos.kt`의 `RecordDto` 정의를 다음으로 교체:

```kotlin
data class RecordDto(
    val id: Long,
    // 서버가 User/Category를 조인해서 이름만 미리 내려준다 — 클라이언트가 별도로 id를 들고
    // 다시 조회할 필요가 없도록 편의상 펼쳐서(flatten) 내려주는 형태.
    @SerializedName("user_name") val userName: String,
    @SerializedName("category_name") val categoryName: String,
    // 측정값 (종목에 따라 초/회 등 단위가 다름 — 실제 단위는 아래 unit 필드로 구분)
    val value: Double,
    // 측정 단위 텍스트 (예: "초", "회"). Category.Unit 컬럼 값.
    val unit: String,
    // 서버가 산출한 등급("특급"~"불합격"). 등급 기준표가 아직 없는 조합이면 null일 수 있음.
    val grade: String?,
    // 이 기록이 어느 세션에서 측정됐는지의 날짜. 세션 없이 등록된 기록이면 null.
    @SerializedName("measured_at") val measuredAt: String?,
    // 사진 첨부 여부. true면 GET /api/records/{id}/photo로 원본을 받아올 수 있다.
    @SerializedName("has_photo") val hasPhoto: Boolean
)
```

- [ ] **Step 2: `RecordRequest`에 `photoBase64` 추가**

`Dtos.kt`의 `RecordRequest` 정의를 다음으로 교체:

```kotlin
data class RecordRequest(
    // 어느 측정 세션에 속한 기록인지. 세션 없이 개별 등록도 허용하는 경우를 위해 nullable.
    @SerializedName("session_id") val sessionId: Long?,
    // 대상 대원의 id. RecordInputFragment의 대원 스피너 선택값에서 가져온다.
    @SerializedName("user_id") val userId: Long,
    // 측정 종목 id (1: 3km 달리기, 2: 팔굽혀펴기, 3: 윗몸일으키기 — RecordInputFragment.categoryMap 참고).
    @SerializedName("category_id") val categoryId: Long,
    val value: Double,
    // 특이사항 메모. 빈 문자열이면 화면단에서 null로 변환해 보낸다(ifBlank { null }).
    val note: String?,
    // 촬영한 사진을 리사이즈/압축 후 Base64로 인코딩한 문자열. 사진이 없으면 null.
    @SerializedName("photo_base64") val photoBase64: String? = null
)
```

- [ ] **Step 3: `ApiService`에 사진 조회 엔드포인트 추가**

`ApiService.kt`의 `sessionRecords` 함수 바로 다음(112~113줄 이후, `trend` 함수 앞)에 추가:

```kotlin
    /** 특정 기록에 첨부된 사진 원본을 내려받는다. 본인 기록이거나 관리자만 조회 가능(서버가 검증). */
    @GET("api/records/{id}/photo")
    suspend fun recordPhoto(@Path("id") id: Long): Response<okhttp3.ResponseBody>
```

- [ ] **Step 4: 빌드 확인**

Run:
```
cd D:\LEE\TEST\AdTrainingMonitor
./gradlew.bat :app:compileDebugKotlin --console=plain
```

Expected: `BUILD SUCCESSFUL` (아직 `hasPhoto`/`photoBase64`를 안 쓰는 호출부는 이번 태스크에서 안 건드렸으므로, 컴파일 에러가 나면 안 된다 — `RecordRequest`의 새 필드는 기본값 `null`이 있어 기존 생성 호출은 그대로 컴파일된다).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/training/monitor/data/model/Dtos.kt app/src/main/java/com/training/monitor/data/api/ApiService.kt
git commit -m "feat: add photo fields to record DTOs and photo download endpoint"
```

---

## Task 5: `PhotoViewActivity` — 서버 조회 모드 추가

**Files:**
- Modify: `D:\LEE\TEST\AdTrainingMonitor\app\src\main\java\com\training\monitor\ui\photo\PhotoViewActivity.kt`

**Interfaces:**
- Consumes: `ApiService.recordPhoto` (Task 4), `RetrofitClient.create(Context)`.
- Produces: `PhotoViewActivity.newIntentForRecord(context, recordId): Intent` — Task 7, 8이 사용.

- [ ] **Step 1: 전체 파일 교체**

`PhotoViewActivity.kt` 전체를 다음으로 교체:

```kotlin
// 촬영한 사진을 전체화면으로 보여주는 화면 — 로컬 Uri(촬영 직후 미리보기) 또는
// 서버에 저장된 기록의 recordId(사후 열람) 두 방식으로 열 수 있다.

package com.training.monitor.ui.photo

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.training.monitor.data.api.RetrofitClient
import com.training.monitor.databinding.ActivityPhotoViewBinding
import kotlinx.coroutines.launch

class PhotoViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnClose.setOnClickListener { finish() }

        val photoUri = intent.getStringExtra(EXTRA_PHOTO_URI)?.toUri()
        val recordId = intent.getLongExtra(EXTRA_RECORD_ID, -1L)
        when {
            photoUri != null -> binding.ivPhoto.setImageURI(photoUri)
            recordId != -1L -> loadPhotoFromServer(recordId)
        }
    }

    /** recordId로 서버의 사진 바이트를 받아와 디코딩해서 보여준다. 실패하면 안내 후 화면을 닫는다. */
    private fun loadPhotoFromServer(recordId: Long) {
        lifecycleScope.launch {
            val bitmap = try {
                val response = RetrofitClient.create(this@PhotoViewActivity).recordPhoto(recordId)
                val bytes = if (response.isSuccessful) response.body()?.bytes() else null
                bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            } catch (e: Exception) {
                null
            }

            if (bitmap != null) {
                binding.ivPhoto.setImageBitmap(bitmap)
            } else {
                Toast.makeText(this@PhotoViewActivity, "사진을 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        private const val EXTRA_PHOTO_URI = "photoUri"
        private const val EXTRA_RECORD_ID = "recordId"

        /** 아직 서버에 저장하지 않은 로컬 촬영 파일(Uri)을 바로 보여줄 때 사용. */
        fun newIntent(context: Context, photoUri: Uri): Intent =
            Intent(context, PhotoViewActivity::class.java)
                .putExtra(EXTRA_PHOTO_URI, photoUri.toString())

        /** 서버에 이미 저장된 기록의 사진을 recordId로 받아와 보여줄 때 사용. */
        fun newIntentForRecord(context: Context, recordId: Long): Intent =
            Intent(context, PhotoViewActivity::class.java)
                .putExtra(EXTRA_RECORD_ID, recordId)
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run:
```
cd D:\LEE\TEST\AdTrainingMonitor
./gradlew.bat :app:compileDebugKotlin --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/training/monitor/ui/photo/PhotoViewActivity.kt
git commit -m "feat: support viewing a record's photo from the server"
```

---

## Task 6: 기록 입력 화면 — 사진 촬영/미리보기/저장

**Files:**
- Modify: `D:\LEE\TEST\AdTrainingMonitor\app\src\main\res\layout\fragment_record_input.xml`
- Modify: `D:\LEE\TEST\AdTrainingMonitor\app\src\main\java\com\training\monitor\ui\record\RecordInputFragment.kt`
- Modify: `D:\LEE\TEST\AdTrainingMonitor\app\src\main\java\com\training\monitor\ui\record\RecordInputViewModel.kt`

**Interfaces:**
- Consumes: `RecordRequest.photoBase64`(Task 4), `PhotoViewActivity.newIntent`(기존), 기존 FileProvider 설정(`AndroidManifest.xml`의 `${applicationId}.fileprovider`, `MainActivity`의 카메라 기능에서 이미 구성됨 — 재사용만 하고 수정하지 않는다).
- Produces: `RecordInputViewModel.saveRecord(sessionId, userId, categoryId, value, note, photoBase64)` — 시그니처 변경.

- [ ] **Step 1: 레이아웃에 사진 UI 추가**

`fragment_record_input.xml`에서 `etNote`를 감싼 `TextInputLayout`이 끝나는 지점(199~205줄) 바로 다음, 그 카드의 바깥쪽 `</LinearLayout>`(206줄) 앞에 삽입:

```xml
                <!-- 사진 첨부 (선택) -->
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical"
                    android:layout_marginTop="12dp">

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btnTakePhoto"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="사진 촬영"
                        app:icon="@android:drawable/ic_menu_camera"
                        style="@style/Widget.MaterialComponents.Button.OutlinedButton"/>

                    <ImageView
                        android:id="@+id/ivPhotoThumbnail"
                        android:layout_width="56dp"
                        android:layout_height="56dp"
                        android:layout_marginStart="12dp"
                        android:scaleType="centerCrop"
                        android:visibility="gone"
                        android:contentDescription="촬영한 사진 미리보기"/>
                </LinearLayout>
```

- [ ] **Step 2: `RecordInputFragment.kt` — import 추가**

파일 맨 위 import 블록(5~22줄)을 다음으로 교체:

```kotlin
import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.training.monitor.R
import com.training.monitor.databinding.DialogCreateSessionBinding
import com.training.monitor.databinding.FragmentRecordInputBinding
import com.training.monitor.ui.photo.PhotoViewActivity
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
```

- [ ] **Step 3: 사진 촬영 상태/런처 필드 추가**

클래스 상단, `categoryMap` 선언(42~46줄) 바로 다음에 추가:

```kotlin
    // 촬영해둔 임시 사진 파일. 저장 시 이 파일을 리사이즈/압축해서 base64로 보낸다.
    private var pendingPhotoFile: File? = null
    private var pendingPhotoUri: Uri? = null

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            binding.ivPhotoThumbnail.visibility = View.VISIBLE
            binding.ivPhotoThumbnail.setImageURI(pendingPhotoUri)
        }
    }
```

- [ ] **Step 4: 촬영/미리보기 클릭 리스너 연결**

`onViewCreated`의 `binding.btnAddSession.setOnClickListener { showCreateSessionDialog() }` 줄(99줄) 바로 다음에 추가:

```kotlin
        binding.btnTakePhoto.setOnClickListener { launchCamera() }
        binding.ivPhotoThumbnail.setOnClickListener {
            pendingPhotoUri?.let { uri -> startActivity(PhotoViewActivity.newIntent(requireContext(), uri)) }
        }
```

- [ ] **Step 5: `launchCamera()`/`encodePhotoBase64()` 메서드 추가**

`saveRecord()` 메서드(181~203줄) 바로 앞에 추가:

```kotlin
    /** 앱 캐시 폴더에 임시 파일을 만들고, FileProvider로 카메라 앱에 촬영을 요청한다. */
    private fun launchCamera() {
        val imagesDir = File(requireContext().cacheDir, "images").apply { mkdirs() }
        val file = File.createTempFile("record_", ".jpg", imagesDir)
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
        pendingPhotoFile = file
        pendingPhotoUri = uri
        cameraLauncher.launch(uri)
    }

    /** 촬영해둔 임시 사진을 긴 변 1280px 이하로 축소하고 JPEG 품질 80으로 압축해 Base64로 인코딩한다. */
    private fun encodePhotoBase64(): String? {
        val file = pendingPhotoFile ?: return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 1280 || bounds.outHeight / sampleSize > 1280) {
            sampleSize *= 2
        }

        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            ?: return null

        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }
```

- [ ] **Step 6: `saveRecord()`에 사진 전달 + 초기화 로직 추가**

`saveRecord()` 메서드(181~203줄)를 다음으로 교체:

```kotlin
    /** 선택된 세션/대원/종목과 입력값을 읽어 ViewModel에 저장을 요청한다. */
    private fun saveRecord() {
        val value = binding.etValue.text.toString().toDoubleOrNull()
        if (value == null) {
            Toast.makeText(requireContext(), "측정값을 입력하세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 스피너의 선택 위치(index)로 원본 리스트에서 실제 세션/대원 객체를 찾는다.
        val sessions = viewModel.sessions.value ?: emptyList()
        val members = viewModel.members.value ?: emptyList()
        val sessionIdx = binding.spinnerSession.selectedItemPosition
        val memberIdx = binding.spinnerMember.selectedItemPosition
        if (sessions.isEmpty() || members.isEmpty()) return

        val categoryId = categoryMap[binding.rgCategory.checkedRadioButtonId] ?: 1L
        viewModel.saveRecord(
            sessionId = sessions[sessionIdx].id,
            userId = members[memberIdx].id,
            categoryId = categoryId,
            value = value,
            note = binding.etNote.text.toString().ifBlank { null },
            photoBase64 = encodePhotoBase64()
        )
    }
```

그리고 `viewModel.saveSuccess.observe(...)` 블록(88~94줄)을 다음으로 교체해 사진 상태도 초기화한다:

```kotlin
        // [MVVM 변경] 저장 성공 이벤트를 관찰해 값/메모/사진 입력란만 초기화한다 (세션·대원 선택은 유지).
        viewModel.saveSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                binding.etValue.text?.clear()
                binding.etNote.text?.clear()
                pendingPhotoFile = null
                pendingPhotoUri = null
                binding.ivPhotoThumbnail.setImageURI(null)
                binding.ivPhotoThumbnail.visibility = View.GONE
                viewModel.onSaveHandled()
            }
        }
```

- [ ] **Step 7: `RecordInputViewModel.kt` — `saveRecord` 시그니처 변경**

`saveRecord` 함수(132~151줄)를 다음으로 교체:

```kotlin
    /** 선택된 세션/대원/종목과 입력값으로 기록 저장 API를 호출한다. photoBase64가 있으면 함께 전송한다. */
    fun saveRecord(sessionId: Long, userId: Long, categoryId: Long, value: Double, note: String?, photoBase64: String? = null) {
        val req = RecordRequest(sessionId, userId, categoryId, value, note, photoBase64)
        val api = RetrofitClient.create(getApplication())
        viewModelScope.launch {
            try {
                val response = api.createRecord(req)
                if (response.isSuccessful) {
                    // 서버가 산출한 최종 등급을 응답 바디에서 꺼내 안내 메시지에 표시
                    val grade = response.body()?.get("grade")
                    _toastMessage.value = "저장 완료 (등급: $grade)"
                    _saveSuccess.value = true
                } else {
                    _toastMessage.value = "저장 실패"
                }
            } catch (e: Exception) {
                _toastMessage.value = "오류: ${e.message}"
            }
        }
    }
```

- [ ] **Step 8: 빌드 확인**

Run:
```
cd D:\LEE\TEST\AdTrainingMonitor
./gradlew.bat :app:assembleDebug --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/res/layout/fragment_record_input.xml app/src/main/java/com/training/monitor/ui/record/RecordInputFragment.kt app/src/main/java/com/training/monitor/ui/record/RecordInputViewModel.kt
git commit -m "feat: capture and attach a photo when saving a record"
```

---

## Task 7: 대원 "내 기록" 목록 — 사진 아이콘 + 열람

**Files:**
- Modify: `D:\LEE\TEST\AdTrainingMonitor\app\src\main\res\layout\item_record.xml`
- Modify: `D:\LEE\TEST\AdTrainingMonitor\app\src\main\java\com\training\monitor\ui\record\RecordAdapter.kt`
- Modify: `D:\LEE\TEST\AdTrainingMonitor\app\src\main\java\com\training\monitor\ui\record\MyRecordFragment.kt`

**Interfaces:**
- Consumes: `RecordDto.hasPhoto`(Task 4), `PhotoViewActivity.newIntentForRecord`(Task 5).
- Produces: `RecordAdapter(onPhotoClick: (RecordDto) -> Unit = {})` — 생성자 시그니처 변경, Task 8도 이 생성자를 사용.

- [ ] **Step 1: `item_record.xml`에 사진 아이콘 추가**

`tvValue` `TextView`(55~61줄) 바로 다음, 카드 안쪽 `</LinearLayout>`(63줄) 앞에 삽입:

```xml
        <!-- 사진 보기 아이콘 (사진이 있는 기록만 표시) -->
        <ImageView
            android:id="@+id/ivPhotoIcon"
            android:layout_width="24dp"
            android:layout_height="24dp"
            android:layout_marginStart="12dp"
            android:src="@android:drawable/ic_menu_camera"
            android:visibility="gone"
            android:contentDescription="사진 보기"/>
```

- [ ] **Step 2: `RecordAdapter.kt` 전체 교체**

```kotlin
// 대원 본인 기록 목록(종목별 필터링된) RecyclerView 어댑터

package com.training.monitor.ui.record

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.training.monitor.data.model.RecordDto
import com.training.monitor.databinding.ItemRecordBinding

/**
 * [RecordDto] 목록을 RecyclerView에 표시하는 어댑터. [MyRecordFragment]와 관리자용
 * [RecordListViewFragment]가 공유해서 쓴다. [onPhotoClick]은 사진이 있는 항목의 카메라
 * 아이콘을 탭했을 때 호출되며, 실제 화면 전환(PhotoViewActivity 실행)은 호출부가 담당한다.
 */
class RecordAdapter(private val onPhotoClick: (RecordDto) -> Unit = {}) :
    ListAdapter<RecordDto, RecordAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemRecordBinding) :
        RecyclerView.ViewHolder(binding.root) {

        /** 측정 기록 한 건의 데이터를 뷰에 바인딩한다. */
        fun bind(record: RecordDto) {
            binding.tvGrade.text = record.grade ?: "-"
            binding.tvGrade.background.mutate().setTint(gradeColor(record.grade))
            binding.tvCategory.text = record.categoryName
            binding.tvDate.text = record.measuredAt ?: "측정일 없음"
            binding.tvValue.text = "${formatValue(record.value)}${record.unit}"
            binding.ivPhotoIcon.visibility = if (record.hasPhoto) View.VISIBLE else View.GONE
            binding.ivPhotoIcon.setOnClickListener { onPhotoClick(record) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    /** ListAdapter가 리스트 갱신 시 항목 동일성/내용 변경 여부를 판단하는 데 사용하는 콜백. */
    companion object DiffCallback : DiffUtil.ItemCallback<RecordDto>() {
        override fun areItemsTheSame(a: RecordDto, b: RecordDto) = a.id == b.id
        override fun areContentsTheSame(a: RecordDto, b: RecordDto) = a == b

        // 등급별 고정 색상 — 특급(우수)일수록 파란 계열, 불합격일수록 붉은 계열 (StatsFragment와 동일한 배색).
        fun gradeColor(grade: String?) = when (grade) {
            "특급" -> Color.parseColor("#1565C0")
            "1급" -> Color.parseColor("#2E7D32")
            "2급" -> Color.parseColor("#F57F17")
            "3급" -> Color.parseColor("#E65100")
            "불합격" -> Color.parseColor("#B71C1C")
            else -> Color.GRAY
        }

        /** 측정값이 정수면 소수점 없이("700"), 아니면 그대로("70.5") 표시한다. */
        fun formatValue(value: Double): String =
            if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    }
}
```

- [ ] **Step 3: `MyRecordFragment.kt` — 어댑터 생성부 교체**

`private val adapter = RecordAdapter()` 줄(37번째)을 다음으로 교체:

```kotlin
    private val adapter = RecordAdapter(onPhotoClick = { record ->
        startActivity(PhotoViewActivity.newIntentForRecord(requireContext(), record.id))
    })
```

그리고 import 블록에 `com.training.monitor.databinding.FragmentMyRecordBinding` 바로 다음 줄에 추가:

```kotlin
import com.training.monitor.ui.photo.PhotoViewActivity
```

- [ ] **Step 4: 빌드 확인**

Run:
```
cd D:\LEE\TEST\AdTrainingMonitor
./gradlew.bat :app:assembleDebug --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/item_record.xml app/src/main/java/com/training/monitor/ui/record/RecordAdapter.kt app/src/main/java/com/training/monitor/ui/record/MyRecordFragment.kt
git commit -m "feat: show photo icon in record list and open full-screen viewer"
```

---

## Task 8: 관리자용 "대원 기록 보기" 화면

**Files:**
- Modify: `D:\LEE\TEST\AdTrainingMonitor\app\src\main\res\layout\item_member.xml`
- Modify: `D:\LEE\TEST\AdTrainingMonitor\app\src\main\java\com\training\monitor\ui\member\MemberAdapter.kt`
- Modify: `D:\LEE\TEST\AdTrainingMonitor\app\src\main\java\com\training\monitor\ui\member\MemberListFragment.kt`
- Modify: `D:\LEE\TEST\AdTrainingMonitor\app\src\main\res\navigation\nav_admin.xml`
- Modify: `D:\LEE\TEST\AdTrainingMonitor\app\src\main\res\layout\fragment_record_list_view.xml`
- Modify: `D:\LEE\TEST\AdTrainingMonitor\app\src\main\java\com\training\monitor\ui\record\RecordListViewViewModel.kt`
- Modify: `D:\LEE\TEST\AdTrainingMonitor\app\src\main\java\com\training\monitor\ui\record\RecordListViewFragment.kt`

**Interfaces:**
- Consumes: `RecordAdapter(onPhotoClick)`(Task 7), `PhotoViewActivity.newIntentForRecord`(Task 5), `ApiService.userRecords`(기존).
- Produces: nav action `action_memberListFragment_to_recordListViewFragment` — 이 태스크 안에서만 쓰임(다른 태스크 의존 없음).

- [ ] **Step 1: `item_member.xml`에 "기록 보기" 아이콘 추가**

`tvUnit` `TextView`(53~61줄) 바로 다음, 카드 안쪽 `</LinearLayout>`(63줄) 앞에 삽입:

```xml
        <!-- 기록 보기 -->
        <ImageView
            android:id="@+id/ivViewRecords"
            android:layout_width="28dp"
            android:layout_height="28dp"
            android:layout_marginStart="8dp"
            android:padding="2dp"
            android:src="@android:drawable/ic_menu_recent_history"
            android:contentDescription="기록 보기"/>
```

- [ ] **Step 2: `MemberAdapter.kt`에 두 번째 클릭 콜백 추가**

`var onItemClick: ((MemberDto) -> Unit)? = null` 줄(26번째) 바로 다음에 추가:

```kotlin
    /** "기록 보기" 아이콘 클릭 콜백. 호출부가 관리자용 기록 열람 화면으로 이동시키는 데 사용. */
    var onRecordsClick: ((MemberDto) -> Unit)? = null
```

`bind()` 함수(32~38줄)의 `binding.root.setOnClickListener { onItemClick?.invoke(member) }` 다음 줄에 추가:

```kotlin
            binding.ivViewRecords.setOnClickListener { onRecordsClick?.invoke(member) }
```

- [ ] **Step 3: `MemberListFragment.kt` — 콜백 연결 + 네비게이션**

import 블록 맨 아래에 추가:

```kotlin
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
```

`adapter.onItemClick = { member -> showResetPasswordDialog(member) }` 줄(58번째) 바로 다음에 추가:

```kotlin
        adapter.onRecordsClick = { member ->
            findNavController().navigate(
                R.id.action_memberListFragment_to_recordListViewFragment,
                bundleOf(
                    "userId" to member.id,
                    "memberName" to "${member.rank ?: ""} ${member.name}".trim()
                )
            )
        }
```

- [ ] **Step 4: `nav_admin.xml` — 목적지/액션 추가**

파일 전체를 다음으로 교체:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- 관리자 네비게이션 그래프 -->
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_admin"
    app:startDestination="@id/memberListFragment">

    <fragment
        android:id="@+id/memberListFragment"
        android:name="com.training.monitor.ui.member.MemberListFragment"
        android:label="대원 관리">

        <action
            android:id="@+id/action_memberListFragment_to_recordListViewFragment"
            app:destination="@id/recordListViewFragment"/>
    </fragment>

    <fragment
        android:id="@+id/recordInputFragment"
        android:name="com.training.monitor.ui.record.RecordInputFragment"
        android:label="기록 입력"/>

    <fragment
        android:id="@+id/statsFragment"
        android:name="com.training.monitor.ui.stats.StatsFragment"
        android:label="통계"/>

    <fragment
        android:id="@+id/recordListViewFragment"
        android:name="com.training.monitor.ui.record.RecordListViewFragment"
        android:label="대원 기록"/>

</navigation>
```

- [ ] **Step 5: `fragment_record_list_view.xml` 전체 교체**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- 관리자 - 특정 대원의 전체 기록(사진 포함) 조회 화면 -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="#F5F5F5"
    tools:context=".ui.record.RecordListViewFragment">

    <TextView
        android:id="@+id/tvMemberName"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="16dp"
        android:textSize="18sp"
        android:textStyle="bold"
        android:textColor="#1A237E"
        android:background="@color/white"/>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rvRecords"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:padding="8dp"
        android:clipToPadding="false"/>

</LinearLayout>
```

- [ ] **Step 6: `RecordListViewViewModel.kt` 전체 교체**

```kotlin
// 관리자 - 특정 대원의 전체 기록 조회 화면의 상태와 서버 통신을 담당하는 ViewModel

package com.training.monitor.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.RetrofitClient
import com.training.monitor.data.model.RecordDto
import kotlinx.coroutines.launch

class RecordListViewViewModel(application: Application) : AndroidViewModel(application) {

    private val _records = MutableLiveData<List<RecordDto>>(emptyList())
    val records: LiveData<List<RecordDto>> = _records

    private val _toastMessage = MutableLiveData<String?>(null)
    val toastMessage: LiveData<String?> = _toastMessage

    /** 지정한 대원(userId)의 전체 측정 기록을 불러와 [records]를 갱신한다. */
    fun loadRecords(userId: Long) {
        val api = RetrofitClient.create(getApplication())
        viewModelScope.launch {
            try {
                val response = api.userRecords(userId)
                _records.value = if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
            } catch (e: Exception) {
                _toastMessage.value = "기록 로딩 실패"
            }
        }
    }

    fun onToastMessageShown() {
        _toastMessage.value = null
    }
}
```

- [ ] **Step 7: `RecordListViewFragment.kt` 전체 교체**

```kotlin
// 관리자 - 특정 대원의 전체 기록(사진 포함) 조회 화면

package com.training.monitor.ui.record

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.training.monitor.databinding.FragmentRecordListViewBinding
import com.training.monitor.ui.photo.PhotoViewActivity

/**
 * 관리자 전용 화면: [com.training.monitor.ui.member.MemberListFragment]에서 대원의
 * "기록 보기" 아이콘을 탭하면 이 화면으로 넘어와, 그 대원의 전체 측정 기록(사진 포함)을
 * 보여준다. [RecordAdapter]를 [MyRecordFragment]와 그대로 공유한다.
 */
class RecordListViewFragment : Fragment() {

    private var _binding: FragmentRecordListViewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecordListViewViewModel by viewModels()
    private val adapter = RecordAdapter(onPhotoClick = { record ->
        startActivity(PhotoViewActivity.newIntentForRecord(requireContext(), record.id))
    })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecordListViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userId = requireArguments().getLong("userId")
        binding.tvMemberName.text = requireArguments().getString("memberName") ?: ""

        binding.rvRecords.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecords.adapter = adapter

        viewModel.records.observe(viewLifecycleOwner) { adapter.submitList(it) }
        viewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                viewModel.onToastMessageShown()
            }
        }

        viewModel.loadRecords(userId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

- [ ] **Step 8: 빌드 확인**

Run:
```
cd D:\LEE\TEST\AdTrainingMonitor
./gradlew.bat :app:assembleDebug --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/res/layout/item_member.xml app/src/main/java/com/training/monitor/ui/member/MemberAdapter.kt app/src/main/java/com/training/monitor/ui/member/MemberListFragment.kt app/src/main/res/navigation/nav_admin.xml app/src/main/res/layout/fragment_record_list_view.xml app/src/main/java/com/training/monitor/ui/record/RecordListViewViewModel.kt app/src/main/java/com/training/monitor/ui/record/RecordListViewFragment.kt
git commit -m "feat: add admin screen to view a member's records and photos"
```

---

## Task 9: 전체 시나리오 수동 확인

**Files:** 없음(코드 변경 없음, 전체 동작 확인만).

- [ ] **Step 1: 백엔드 재시작 + 마이그레이션 적용 확인**

`D:\LEE\TEST\TrainingMonitor`에서 서버를 재시작한다(Development 환경이면 시작 시 자동으로 `db.Database.Migrate()`가 실행되어 Task 1의 마이그레이션이 적용된다). 서버 로그에 마이그레이션 적용 관련 에러가 없는지 확인한다.

- [ ] **Step 2: 관리자 — 사진 촬영 후 저장**

에뮬레이터/실기기에서 관리자로 로그인 → 기록 입력 화면 → "사진 촬영" 버튼으로 촬영 → 썸네일이 보이는지 확인 → 썸네일 탭해서 전체화면으로 보이는지 확인 → 세션/대원/종목/측정값 입력 후 저장 → "저장 완료" 토스트 확인.

- [ ] **Step 3: 대원 — 내 기록에서 사진 열람**

방금 기록을 입력받은 대원 계정으로 로그인 → 내 기록 화면 → 해당 종목 탭에서 방금 저장한 기록에 카메라 아이콘이 보이는지 확인 → 탭해서 전체화면으로 사진이 정상 표시되는지 확인.

- [ ] **Step 4: 관리자 — 대원 기록 보기 화면**

관리자 계정으로 로그인 → 대원 관리 화면 → 방금 그 대원의 "기록 보기" 아이콘 탭 → 대원 이름이 타이틀에 보이고, 목록에 방금 기록과 카메라 아이콘이 보이는지 확인 → 탭해서 사진 확인 → 뒤로가기로 대원 관리 화면으로 돌아온 뒤, 같은 대원의 행(아이콘이 아닌 나머지 영역)을 탭했을 때 여전히 "비밀번호 재설정" 다이얼로그가 뜨는지 확인(회귀 없음).

- [ ] **Step 5: 사진 없이 저장 — 회귀 확인**

사진 없이 새 기록을 저장 → 내 기록/대원 기록 보기 양쪽 목록에 카메라 아이콘이 없는지 확인.
