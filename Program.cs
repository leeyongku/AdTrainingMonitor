// 훈련 모니터링 API 서버 진입점 및 DI 설정

using System.Text;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Diagnostics;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi;
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


// 호스트, 설정(appsettings.json), DI 컨테이너를 준비하는 빌더
var builder = WebApplication.CreateBuilder(args);

// =============================================
// 서비스 등록
// =============================================


// DB: PostgreSQL(Npgsql) 드라이버로 AppDbContext를 DI에 등록 (기본 수명주기: Scoped)
builder.Services.AddDbContext<AppDbContext>(opt =>
    opt.UseNpgsql(builder.Configuration.GetConnectionString("Default"))
       .UseSnakeCaseNamingConvention()
       // 시드 데이터(HasData) 반영 순서 차이로 인한 가양성 경고 방지 — 실제 스키마는 최신 마이그레이션과 일치함
       .ConfigureWarnings(w => w.Ignore(RelationalEventId.PendingModelChangesWarning)));

// JWT 인증
// 토큰 서명 검증에 사용할 비밀키 (appsettings.json의 Jwt:Secret)
var jwtSecret = builder.Configuration["Jwt:Secret"]!;
// 기본 인증 스킴을 JWT Bearer로 지정
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(opt =>
    {
        opt.TokenValidationParameters = new TokenValidationParameters
        {
            // 토큰 서명이 우리가 발급한 키로 서명됐는지 검증
            ValidateIssuerSigningKey = true,
            // 비밀키 문자열을 대칭키(HMAC)로 변환
            IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwtSecret)),
            // 단일 서비스 구조라 발급자/대상자 구분이 필요 없어 검증 생략
            ValidateIssuer = false,
            ValidateAudience = false,
            // 만료 시각을 유예 없이 그대로 적용 (기본값은 5분 유예)
            ClockSkew = TimeSpan.Zero
        };
    });

// 인가(권한 검사) 서비스 등록 — [Authorize] 특성 사용을 위해 필요
builder.Services.AddAuthorization();

// 커스텀 서비스
// JWT 발급/검증 로직을 담당하는 서비스 (요청마다 새 인스턴스)
builder.Services.AddScoped<JwtService>();

// Controllers
// MVC 컨트롤러 기반 라우팅/모델 바인딩 활성화
// JSON 속성명은 snake_case로 직렬화/역직렬화 (Android 클라이언트가 snake_case 계약을 사용)
builder.Services.AddControllers()
    .AddJsonOptions(opt =>
    {
        opt.JsonSerializerOptions.PropertyNamingPolicy = new SnakeCaseNamingPolicy();
        opt.JsonSerializerOptions.DictionaryKeyPolicy = new SnakeCaseNamingPolicy();
    });

// Swagger
// 컨트롤러의 엔드포인트 메타데이터(라우트, 파라미터 등)를 수집
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(c =>
{
    // "v1" 문서 하나만 생성, 제목/버전 표시
    c.SwaggerDoc("v1", new OpenApiInfo { Title = "훈련 모니터링 API", Version = "v1" });

    // Swagger에서 JWT 토큰 입력 가능하도록 설정
    // Swagger UI 상단에 "Authorize" 버튼을 추가하고, 입력한 토큰을 Authorization 헤더로 전송
    c.AddSecurityDefinition("Bearer", new OpenApiSecurityScheme
    {
        Name = "Authorization",
        Type = SecuritySchemeType.Http,
        Scheme = "Bearer",
        BearerFormat = "JWT",
        In = ParameterLocation.Header
    });
    // 모든 엔드포인트에 Bearer 인증 요구사항을 전역 적용 (Swagger UI에 자물쇠 아이콘 표시)
    c.AddSecurityRequirement(document => new OpenApiSecurityRequirement
    {
        [new OpenApiSecuritySchemeReference("Bearer", document)] = []
    });
});

// =============================================
// 미들웨어 파이프라인
// =============================================
// 위에서 등록한 서비스들을 실제로 빌드하여 요청을 처리할 앱 인스턴스 생성
var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    // 개발 환경에서만 Swagger 문서(JSON)와 UI 페이지를 노출
    app.UseSwagger();
    app.UseSwaggerUI();

    // 개발 환경에서만 자동 마이그레이션 (운영 환경은 배포 파이프라인에서 별도 적용)
    // DbContext는 Scoped라 최상위에서 바로 못 쓰므로 임시 스코프를 생성해서 사용
    using var scope = app.Services.CreateScope();
    var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
    // 아직 적용되지 않은 마이그레이션을 순서대로 실행
    db.Database.Migrate();
}

// 인증(신원 확인)이 인가(권한 검사)보다 먼저 실행되어야 함
app.UseAuthentication();
app.UseAuthorization();
// 컨트롤러의 라우트를 파이프라인에 매핑
app.MapControllers();

// 요청 수신 대기 시작 (블로킹)
app.Run();
