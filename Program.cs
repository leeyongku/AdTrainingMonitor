// 훈련 모니터링 API 서버 진입점 및 DI 설정
//
// [ASP.NET Core 앱의 기본 구조]
// 1) "빌더(Builder)"를 만든다 -> 필요한 서비스들을 하나씩 등록(DI 컨테이너에 넣음)
// 2) 빌더로 실제 "앱(App)"을 빌드한다
// 3) 앱이 요청을 처리할 때 거쳐가는 순서(미들웨어 파이프라인)를 정의한다
// 4) app.Run()으로 서버를 켜고 요청을 기다린다
//
// 여기서 "서비스 등록"이란 것은, 나중에 컨트롤러 등에서
// 생성자로 주입받아 쓸 수 있게 "이 타입은 이렇게 만들어서 써라"고
// DI(Dependency Injection, 의존성 주입) 컨테이너에 미리 알려두는 작업입니다.

using System.Text;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Diagnostics;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi;
using Swashbuckle.AspNetCore.Annotations;
using TrainingMonitor.Data;
using TrainingMonitor.Services;

// Check if project already has a UserSecretsId configured
// Get-Content "d:\LEE\TEST\TrainingMonitor\TrainingMonitor.csproj" | Select-String "UserSecretsId"

// Initialize user secrets for the project
// dotnet user-secrets init --project "d:\LEE\TEST\TrainingMonitor\TrainingMonitor.csproj"

// $secret = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
// dotnet user-secrets set "Jwt:Secret" "$secret" --project "d:\LEE\TEST\TrainingMonitor\TrainingMonitor.csproj"
// dotnet user-secrets set "ConnectionStrings:Default" "Host=localhost;Port=5433;Database=training_monitor;Username=postgres;Password=5030" --project "d:\LEE\TEST\TrainingMonitor\TrainingMonitor.csproj"

// dotnet user-secrets list --project "d:\LEE\TEST\TrainingMonitor\TrainingMonitor.csproj"


// [1단계] 빌더 생성
// - args: 프로그램 실행 시 넘어온 커맨드라인 인자
// - builder.Configuration: appsettings.json + User Secrets + 환경변수 등을 합쳐서 읽는 설정 객체
// - builder.Services: 여기에 서비스를 하나씩 등록(=DI 컨테이너 구성)
var builder = WebApplication.CreateBuilder(args);

// [Windows 서비스로 실행 지원]
// 콘솔(dotnet run)로 실행될 때는 아무 영향이 없고, Windows 서비스로 등록되어 SCM(서비스 제어 관리자)이
// 구동했을 때만 서비스 생명주기(시작/중지 신호, 이벤트 로그 연동 등)에 맞게 동작하도록 호스팅 모델을 전환한다.
// 부팅 시 자동 시작하려면 배포 후 sc.exe(또는 New-Service)로 서비스 등록 + 시작 유형을 자동으로 설정해야 한다.
builder.Host.UseWindowsService();

// =============================================
// 서비스 등록 (아직 서버는 켜지지 않은 상태 - "재료 준비" 단계)
// =============================================


// [DB 연결 설정]
// AppDbContext(우리가 만든 EF Core용 DB 접근 클래스)를 DI에 등록.
// - UseNpgsql(...): PostgreSQL 드라이버(Npgsql)를 사용하고, 연결 문자열은
//   appsettings.json(또는 User Secrets)의 "ConnectionStrings:Default" 값을 사용
// - UseSnakeCaseNamingConvention(): C# 프로퍼티명(PascalCase, 예: MilitaryId)을
//   DB 컬럼명(snake_case, 예: military_id)으로 자동 변환
// - ConfigureWarnings(...): 시드 데이터(HasData)가 마이그레이션 적용 순서상
//   실제 DB와 미세하게 다르게 보여서 뜨는 오탐(가양성) 경고를 무시
// - 기본 수명주기는 Scoped: HTTP 요청 1건마다 새 인스턴스가 만들어지고,
//   요청이 끝나면 함께 정리됨(같은 요청 안에서는 항상 같은 인스턴스 재사용)
builder.Services.AddDbContext<AppDbContext>(opt =>
    opt.UseNpgsql(builder.Configuration.GetConnectionString("Default"))
       .UseSnakeCaseNamingConvention()
       .ConfigureWarnings(w => w.Ignore(RelationalEventId.PendingModelChangesWarning)));

// [JWT 인증 설정]
// JWT(Json Web Token)는 로그인 성공 시 서버가 발급해주는 "출입증" 같은 토큰입니다.
// 클라이언트는 이후 요청마다 Authorization 헤더에 이 토큰을 담아 보내고,
// 서버는 토큰의 서명을 검증해서 "이 사람이 맞다"를 확인합니다.

// 토큰 서명(암호화 서명)에 사용할 비밀키 문자열. appsettings.json의 "Jwt:Secret" 값을 읽어옴
// (뒤의 !는 "이 값은 null이 아님을 내가 보장한다"는 뜻의 null 허용 해제 연산자)
var jwtSecret = builder.Configuration["Jwt:Secret"]!;

// 이 앱에서 기본으로 사용할 인증 방식을 "JWT Bearer 방식"으로 지정
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(opt =>
    {
        // 들어온 토큰을 어떤 기준으로 검증할지 정의
        opt.TokenValidationParameters = new TokenValidationParameters
        {
            // 토큰의 서명이 우리 서버가 발급할 때 쓴 비밀키와 일치하는지 검증
            // (다른 사람이 임의로 만든 위조 토큰을 걸러내는 핵심 검증)
            ValidateIssuerSigningKey = true,
            // 비밀키 문자열을 바이트로 변환해 HMAC 대칭키 형태로 사용
            IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwtSecret)),
            // Issuer(발급자)/Audience(대상자) 값 검증은 생략
            // - 이 프로젝트는 서버가 하나뿐이라 "누가 발급했는지/누구를 위한 건지" 구분이 필요 없음
            ValidateIssuer = false,
            ValidateAudience = false,
            // 토큰 만료 시각 검증 시 유예 시간을 0으로 설정
            // (기본값은 5분 유예를 줘서, 만료돼도 5분간은 허용됨 - 여기선 그 여유를 없앰)
            ClockSkew = TimeSpan.Zero
        };
    });

// [인가(Authorization) 서비스 등록]
// 인증(Authentication)이 "너 누구야?"를 확인하는 거라면,
// 인가(Authorization)는 "너 이거 할 권한 있어?"를 확인하는 것.
// 컨트롤러에 붙이는 [Authorize] 특성(attribute)을 쓰려면 이 등록이 필요함
builder.Services.AddAuthorization();

// [우리가 직접 만든 서비스 등록]
// JwtService: 토큰 발급(로그인 성공 시)과 검증 로직을 담당하는 클래스
// AddScoped: 요청 1건마다 새로 만들어짐 (DB 컨텍스트와 생명주기를 맞추기 위함)
builder.Services.AddScoped<JwtService>();

// [MVC 컨트롤러 등록]
// Controllers 폴더의 클래스들(AuthController 등)이 실제로 HTTP 요청을 처리할 수 있도록
// 라우팅(URL -> 메서드 매핑)과 모델 바인딩(요청 body/query -> C# 객체 변환) 기능을 활성화
//
// .AddJsonOptions(...): JSON으로 주고받을 때 속성 이름 규칙을 커스터마이징
// - 기본은 C# 관례인 camelCase(예: militaryId)
// - 여기서는 SnakeCaseNamingPolicy를 적용해 snake_case(예: military_id)로 강제 변환
// - 이유: 이 API를 호출하는 Android 앱 쪽이 snake_case로 JSON을 주고받기로 약속했기 때문
//   (PropertyNamingPolicy: 요청/응답 바디의 속성명, DictionaryKeyPolicy: Dictionary를 JSON으로 만들 때의 키 이름)
builder.Services.AddControllers()
    .AddJsonOptions(opt =>
    {
        opt.JsonSerializerOptions.PropertyNamingPolicy = new SnakeCaseNamingPolicy();
        opt.JsonSerializerOptions.DictionaryKeyPolicy = new SnakeCaseNamingPolicy();
    });

// [Swagger 설정]
// Swagger는 "이 API에 어떤 엔드포인트가 있고, 어떤 파라미터를 받는지"를
// 웹 페이지(/swagger)에서 눈으로 보고 직접 테스트도 해볼 수 있게 해주는 도구입니다.
        
// 컨트롤러들의 라우트/파라미터 정보를 수집해 문서화할 수 있게 준비
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(c =>
{
    // "v1"이라는 이름의 API 문서 하나를 생성. Swagger UI 상단에 표시될 제목/버전
    c.SwaggerDoc("v1", new OpenApiInfo { Title = "훈련 모니터링 API", Version = "v1" });

    // [SwaggerOperation] 등 어노테이션 특성을 Swagger 문서 생성 시 반영
    c.EnableAnnotations();

    // Swagger UI에서 JWT 토큰을 직접 입력해서 인증이 필요한 API도 테스트할 수 있게 설정
    // -> 화면 우측 상단에 "Authorize" 자물쇠 버튼이 생기고,
    //    거기에 토큰을 입력하면 이후 모든 요청의 Authorization 헤더에 자동으로 붙어서 전송됨
    c.AddSecurityDefinition("Bearer", new OpenApiSecurityScheme
    {
        Name = "Authorization",
        Type = SecuritySchemeType.Http,
        Scheme = "Bearer",
        BearerFormat = "JWT",
        In = ParameterLocation.Header
    });
    // 위에서 정의한 Bearer 인증을 모든 엔드포인트에 기본 요구사항으로 전역 적용
    // (Swagger UI에서 각 API 옆에 자물쇠 아이콘이 표시됨)
    c.AddSecurityRequirement(document => new OpenApiSecurityRequirement
    {
        [new OpenApiSecuritySchemeReference("Bearer", document)] = []
    });
});

// =============================================
// 미들웨어 파이프라인 ("재료 준비" 끝, 이제 실제로 조리해서 요청을 처리할 차례)
// =============================================
// 미들웨어란: 하나의 HTTP 요청이 들어와서 응답이 나갈 때까지 순서대로 거쳐가는
// 처리 단계들입니다. 아래 등록한 순서가 곧 요청이 처리되는 순서입니다.

// 지금까지 등록한 서비스 설정을 바탕으로 실제 실행 가능한 앱 인스턴스를 생성
var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    // 개발 환경(로컬에서 실행할 때)에서만 Swagger 문서(JSON)와 UI 페이지를 켬
    // 운영 환경에서는 API 구조가 외부에 노출되지 않도록 꺼둠
    app.UseSwagger();
    app.UseSwaggerUI();

    // 개발 환경에서만 서버 시작 시 자동으로 DB 마이그레이션을 적용
    // (운영 환경은 실수로 DB가 바뀌지 않도록, 별도의 배포 파이프라인에서 수동/통제된 방식으로 적용)
    //
    // AppDbContext는 Scoped 수명주기라 프로그램 최상위 코드에서 바로 꺼내 쓸 수 없음
    // (Scoped는 "요청 하나" 단위로 만들어지는데, 지금은 요청 중이 아니라 서버 시작 시점이기 때문)
    // 그래서 using var scope로 임시 스코프를 하나 만들어 그 안에서 DbContext를 꺼내 씀
    using var scope = app.Services.CreateScope();
    var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
    // Migrations 폴더에 있는 마이그레이션 중 아직 DB에 적용 안 된 것들을 순서대로 실행
    // (테이블 생성/컬럼 추가/시드 데이터 삽입 등)
    db.Database.Migrate();
}

// 인증(Authentication, "너 누구야?" 확인)이 인가(Authorization, "권한 있어?" 확인)보다
// 반드시 먼저 실행되어야 함 - 순서를 바꾸면 신원 확인 전에 권한 검사를 하게 되어 오작동함
app.UseAuthentication();
app.UseAuthorization();

// 위에서 등록한 컨트롤러들의 [Route]/[HttpGet]/[HttpPost] 등을 실제 URL 라우팅에 연결
// (이 줄이 있어야 "/api/auth/login" 같은 요청이 AuthController.Login으로 연결됨)
app.MapControllers();

// 서버를 실제로 켜고, 요청이 들어올 때까지 대기(블로킹)
// 이 줄 이후의 코드는 서버가 종료될 때까지 실행되지 않음
app.Run();
