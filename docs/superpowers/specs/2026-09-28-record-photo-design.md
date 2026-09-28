# 기록 사진 첨부 기능 설계

## 1. 개요

관리자가 기록 입력 화면(`RecordInputFragment`)에서 측정 기록을 저장할 때 사진을 함께 첨부할 수 있게 하고, 대원 본인의 "내 기록" 화면(`MyRecordFragment`)에서 그 사진을 열람할 수 있게 한다. 사진은 선택 항목이며, 서버 DB(`records` 테이블)에 바이너리로 직접 저장한다.

## 2. 범위

**포함**
- 기록 저장 시 사진 1장 선택 첨부(카메라 촬영만 지원, 갤러리 선택은 범위 밖).
- 서버 DB(bytea 컬럼)에 사진 바이너리 저장.
- 대원의 "내 기록" 목록에서 사진이 있는 기록에 아이콘 표시 → 탭 시 전체화면 열람.
- 관리자가 기록 입력 화면에서 촬영 직후(저장 전) 로컬 미리보기로 바로 확인.
- 관리자용 "대원 기록 보기" 화면 신설 — 대원 목록에서 대원을 선택해 그 대원의 저장된 전체 기록(사진 포함)을 열람.

**제외 (YAGNI)**
- 기록당 여러 장 첨부.
- 사진 수정/삭제(재촬영으로 교체는 저장 전까지만 가능, 저장 후 사진 교체 기능은 없음).
- 갤러리에서 기존 사진 선택.
- 이미지 CDN/외부 스토리지 연동.

## 3. 데이터 모델

`Models/Entities.cs`의 `Record` 엔티티에 컬럼 추가:

```csharp
public byte[]? Photo { get; set; }
```

새 EF Core 마이그레이션으로 `records` 테이블에 `photo bytea NULL` 컬럼을 추가한다. 기존 레코드는 전부 `NULL`(사진 없음)로 유지되므로 백필 로직은 필요 없다.

## 4. API 명세

### 4.1 `POST /api/records` (기존 엔드포인트 확장)

`RecordRequest`에 필드 추가:

```csharp
public record RecordRequest(long? SessionId, long UserId, long CategoryId, double Value, string? Note, string? PhotoBase64);
```

- `PhotoBase64`가 null이 아니면 `Convert.FromBase64String`으로 디코딩해 `Record.Photo`에 저장한다.
- 디코딩 실패(잘못된 base64 문자열) 시 400 Bad Request를 반환한다.
- 기존 동작(세션/대원/종목 검증, 중복 방지, 등급 산출)은 변경 없음.

### 4.2 기록 조회 응답 (`RecordDto`) 확장

```csharp
public record RecordDto(long Id, string UserName, string CategoryName, double Value, string Unit, string? Grade, DateOnly? MeasuredAt, bool HasPhoto);
```

`GetRecordDtos`의 프로젝션에 `HasPhoto = r.Photo != null`을 추가한다. 목록 응답 용량을 줄이기 위해 사진 바이트 자체는 목록 응답에 포함하지 않는다.

### 4.3 신규: `GET /api/records/{id}/photo`

- 인증 필요(`[Authorize]`, 컨트롤러 기본 정책 그대로).
- 권한: 요청자가 ADMIN이거나, 요청자 본인의 기록(`record.UserId == 로그인한 사용자 id`)인 경우만 허용. 그 외에는 403.
- 기록이 없으면 404, 있지만 `Photo`가 null이면 404.
- 성공 시 `Content-Type: image/jpeg`로 바이너리를 그대로 응답(`File(record.Photo, "image/jpeg")`).

## 5. 안드로이드 변경

### 5.1 DTO/`ApiService`

- `Dtos.kt`: `RecordDto`에 `@SerializedName("has_photo") val hasPhoto: Boolean` 추가, `RecordRequest`에 `@SerializedName("photo_base64") val photoBase64: String?` 추가.
- `ApiService.kt`: `@GET("api/records/{id}/photo") suspend fun recordPhoto(@Path("id") id: Long): Response<okhttp3.ResponseBody>` 추가.

### 5.2 `fragment_record_input.xml`

"측정값 입력" 카드 안, 특이사항 입력란 아래에 사진 섹션 추가:
- "사진 촬영" 버튼(카메라 아이콘 + 텍스트).
- 촬영 후에만 보이는 정사각형 썸네일 `ImageView`(탭하면 전체화면 미리보기).

### 5.3 `RecordInputFragment.kt`

- `MainActivity`의 카메라 플로우(임시 캐시 파일 + `FileProvider` + `ActivityResultContracts.TakePicture()`)를 Fragment 버전으로 재사용해 사진 촬영.
- 촬영 성공 시: 임시 파일의 `Uri`를 필드에 보관하고, 썸네일 `ImageView.setImageURI(uri)`로 미리보기 표시. 썸네일 탭 시 `PhotoViewActivity.newIntent(context, uri)`(기존 로컬 Uri 방식 그대로)로 전체화면 확인.
- 저장 버튼(`saveRecord()`) 클릭 시: 보관해둔 임시 파일이 있으면 `BitmapFactory.decodeFile` + `inSampleSize`로 긴 변 1280px 이하로 축소 → JPEG 품질 80으로 재압축 → `Base64.encodeToString`으로 인코딩해 `RecordRequest.photoBase64`에 실어 전송. 없으면 `null`.
- 저장 성공 시(`saveSuccess` 관찰) 기존 값/메모 초기화에 더해 임시 사진 상태(Uri, 썸네일)도 초기화한다.

### 5.4 `RecordInputViewModel.kt`

`saveRecord(...)` 시그니처에 `photoBase64: String? = null` 파라미터를 추가하고 `RecordRequest` 생성 시 그대로 전달한다.

### 5.5 `RecordAdapter.kt` / `item_record.xml`

- `item_record.xml`에 작은 카메라 아이콘 `ImageView`(사진 없으면 `GONE`) 추가.
- `RecordAdapter`: 생성자에 `onPhotoClick: (RecordDto) -> Unit` 콜백을 받아, `bind()`에서 `record.hasPhoto`에 따라 아이콘 가시성을 토글하고 클릭 시 콜백 호출.
- `MyRecordFragment`가 어댑터 생성 시 콜백으로 `PhotoViewActivity.newIntentForRecord(requireContext(), record.id)`를 실행하도록 연결.

### 5.6 `PhotoViewActivity.kt`

기존에는 로컬 `Uri`만 표시했는데, 서버에서 받아와 표시하는 두 번째 진입 방식을 추가한다.

- 새 companion 함수: `fun newIntentForRecord(context: Context, recordId: Long): Intent` — `EXTRA_RECORD_ID`로 id만 전달.
- `onCreate`에서 `EXTRA_PHOTO_URI`가 있으면 기존처럼 `setImageURI`, `EXTRA_RECORD_ID`가 있으면 `lifecycleScope.launch`로 `RetrofitClient.create(this).recordPhoto(id)` 호출 → 성공 시 `response.body()?.bytes()`를 `BitmapFactory.decodeByteArray`로 디코딩해 `ivPhoto.setImageBitmap(bitmap)`. 실패/404 시 Toast 안내 후 `finish()`.

### 5.7 관리자용 "대원 기록 보기" 화면 (기존 빈 스캐폴드 구현)

현재 `RecordListViewFragment`/`RecordListViewViewModel`은 Android Studio가 생성해둔 채로 아무 기능이 없는 빈 스캐폴드(TODO, "Hello" placeholder)다. 이를 실제로 구현해 관리자용 기록 열람 화면으로 쓴다.

- `item_member.xml` / `MemberAdapter.kt`: 항목에 "기록 보기" 아이콘 버튼을 추가한다. 기존 행 전체 탭(비밀번호 재설정)은 그대로 두고, 새 아이콘 클릭에만 별도 콜백(`onRecordsClick: (MemberDto) -> Unit`)을 연결한다.
- `nav_admin.xml`: `recordListViewFragment` destination과 `memberListFragment → recordListViewFragment` action을 추가한다. 인자로 `userId`(Long)와 `memberName`(String, 화면 타이틀용)을 전달한다.
- `RecordListViewFragment`: `arguments`에서 `userId`/`memberName`을 읽어 화면 타이틀에 표시하고, `RecordListViewViewModel.loadRecords(userId)`로 기존 `GET /api/records/user/{userId}` API를 호출한다. 결과를 `RecordAdapter`(5.5에서 만든 사진 클릭 콜백 포함)로 RecyclerView에 표시한다.
- `RecordListViewViewModel`: `userId`를 받아 `ApiService.userRecords(userId)`를 호출하고 결과를 `LiveData<List<RecordDto>>`로 노출한다 (기존 `MyRecordViewModel`과 동일한 패턴).
- `fragment_record_list_view.xml`: 기존 "Hello" `TextView`를 대원 이름 타이틀 `TextView` + `RecyclerView`로 교체한다.

## 6. 에러 처리

- 사진 없이 저장: 기존과 동일하게 동작(회귀 없음).
- 사진 다운로드 실패(네트워크 오류, 403, 404): `PhotoViewActivity`에서 Toast로 안내.
- base64 디코딩 실패(손상된 데이터): 서버가 400 반환, 안드로이드는 기존 `saveRecord`의 실패 처리 경로(토스트)를 그대로 탄다.

## 7. 보안 고려사항

- 사진 조회 API는 본인 또는 ADMIN만 허용 — 다른 대원의 사진을 URL 추측만으로 볼 수 없어야 한다.
- 사진은 JWT 인증이 필요한 API로만 접근 가능하며, 별도의 공개 URL은 만들지 않는다.

## 8. 테스트 계획

- 백엔드: `dotnet build`로 컴파일 확인, 마이그레이션 적용 확인.
- 안드로이드: `./gradlew assembleDebug`로 컴파일 확인.
- 수동 시나리오(에뮬레이터 또는 실기기):
  1. 관리자로 로그인 → 기록 입력 화면에서 사진 촬영 → 미리보기 표시 확인 → 저장.
  2. 대원 계정으로 로그인 → 내 기록 목록에서 해당 기록에 카메라 아이콘이 보이는지 확인 → 탭해서 전체화면으로 사진이 정상 표시되는지 확인.
  3. 사진 없이 기록 저장 → 목록에 카메라 아이콘이 없는지 확인(회귀 없음).
  4. 관리자로 로그인 → 대원 관리 화면에서 "기록 보기" 아이콘 탭 → 그 대원의 기록 목록(사진 포함)이 보이는지, 기존 "행 탭 = 비밀번호 재설정"이 여전히 동작하는지 확인.
