// JWT 토큰 발급 및 검증 서비스

using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using Microsoft.IdentityModel.Tokens;
using TrainingMonitor.Models.Entities;

namespace TrainingMonitor.Services;


//JWT(JSON Web Token)를 생성하고, 검증하고, 파싱하는 역할을 전담하는 서비스 클래스
// 주 생성자(primary constructor)로 IConfiguration을 주입받아 필드 초기화에 바로 사용
public class JwtService(IConfiguration config)
{
    // 토큰 서명/검증에 쓰는 비밀키 (appsettings.json의 Jwt:Secret)
    private readonly string _secret = config["Jwt:Secret"]!;
    // Access Token 만료 시간(분)
    private readonly int _accessExpiry = int.Parse(config["Jwt:AccessTokenExpiryMinutes"]!);
    // Refresh Token 만료 시간(일)
    private readonly int _refreshExpiry = int.Parse(config["Jwt:RefreshTokenExpiryDays"]!);

    // 로그인 성공 시 발급하는 단기 토큰. API 요청마다 Authorization 헤더로 전달됨
    public string GenerateAccessToken(User user)
    {
        // 비밀키 문자열을 대칭키(HMAC)로 변환
        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(_secret));
        // HMAC-SHA256으로 서명
        var creds = new SigningCredentials(key, SecurityAlgorithms.HmacSha256);

        // 토큰에 담을 사용자 식별 정보 (컨트롤러에서 User.Identity로 읽음)
        var claims = new[]
        {
            new Claim(ClaimTypes.NameIdentifier, user.Id.ToString()),
            new Claim(ClaimTypes.Role, user.Role.ToString()),
            new Claim(ClaimTypes.Name, user.Name)
        };

        var token = new JwtSecurityToken(
            claims: claims,
            expires: DateTime.UtcNow.AddMinutes(_accessExpiry),
            signingCredentials: creds
        );

        // JwtSecurityToken 객체를 실제 전송 가능한 문자열(xxx.yyy.zzz)로 직렬화
        return new JwtSecurityTokenHandler().WriteToken(token);
    }

    // Access Token 만료 후 재발급용으로 쓰는 장기 토큰. 사용자 식별자만 담아 페이로드를 최소화
    public string GenerateRefreshToken(long userId)
    {
        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(_secret));
        var creds = new SigningCredentials(key, SecurityAlgorithms.HmacSha256);

        var token = new JwtSecurityToken(
            claims: [new Claim(ClaimTypes.NameIdentifier, userId.ToString())],
            expires: DateTime.UtcNow.AddDays(_refreshExpiry),
            signingCredentials: creds
        );

        return new JwtSecurityTokenHandler().WriteToken(token);
    }

    // 토큰 문자열의 서명과 만료 여부를 검증하고, 유효하면 클레임 정보를 반환
    // Program.cs의 JwtBearer 미들웨어와 별개로 서비스 코드에서 직접 토큰을 검증할 때 사용 (예: Refresh Token 재발급 로직)
    public ClaimsPrincipal? ValidateToken(string token)
    {
        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(_secret));
        try
        {
            return new JwtSecurityTokenHandler().ValidateToken(token,
                new TokenValidationParameters
                {
                    ValidateIssuerSigningKey = true,
                    IssuerSigningKey = key,
                    // 단일 서비스 구조라 발급자/대상자 검증은 생략
                    ValidateIssuer = false,
                    ValidateAudience = false,
                    // 만료 시각을 유예 없이 그대로 적용
                    ClockSkew = TimeSpan.Zero
                }, out _);
        }
        // 서명 불일치, 만료, 형식 오류 등 어떤 이유로든 검증 실패 시 null 반환 (호출부에서 401 처리)
        catch { return null; }
    }

    // Refresh Token을 DB(refresh_tokens 테이블)에 저장할 때 함께 기록할 만료 시각
    public DateTime RefreshTokenExpiry => DateTime.UtcNow.AddDays(_refreshExpiry);
}
