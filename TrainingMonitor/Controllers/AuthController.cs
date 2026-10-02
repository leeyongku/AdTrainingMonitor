// 로그인, 토큰 갱신, 로그아웃 API

using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using System.Security.Claims;
using TrainingMonitor.Data;
using TrainingMonitor.Models.Entities;
using TrainingMonitor.Services;
using BCrypt.Net;
using Swashbuckle.AspNetCore.Annotations;

namespace TrainingMonitor.Controllers;

[ApiController]
[Route("api/auth")]
public class AuthController(AppDbContext db, JwtService jwtService) : ControllerBase
{
    public record LoginRequest(string MilitaryId, string Password);
    public record RefreshRequest(string RefreshToken);
    public record TokenResponse(string AccessToken, string RefreshToken, string Role, bool MustChangePassword);
    public record ChangePasswordRequest(string CurrentPassword, string NewPassword);


    /// <summary>
    /// 로그인 - 군번/비밀번호 검증 후 Access/Refresh Token 발급
    /// </summary>
    /// <param name="req">군번(MilitaryId)과 비밀번호가 담긴 로그인 요청</param>
    /// <returns>Access/Refresh Token, 역할(Role), 비밀번호 변경 필요 여부(MustChangePassword)</returns>
    [HttpPost("login")]
    [SwaggerOperation(
    Summary = "사용자 로그인",
    Description = "JWT 인증이 필요합니다.\n\n" +
                  "사용자 로그인 시 군번과 비밀번호를 검증하고, Access Token과 Refresh Token을 발급합니다."
    )]
    public async Task<ActionResult<TokenResponse>> Login([FromBody] LoginRequest req)
    {
        var user = await db.Users
            .Include(u => u.Unit)
            .FirstOrDefaultAsync(u => u.MilitaryId == req.MilitaryId && u.IsActive);

        // 존재하지 않는 군번이거나 비활성화된 계정이거나, 비밀번호 해시가 일치하지 않으면 인증 실패
        if (user is null || !BCrypt.Net.BCrypt.Verify(req.Password, user.PasswordHash))
            return Unauthorized("군번 또는 비밀번호가 올바르지 않습니다.");

        var accessToken = jwtService.GenerateAccessToken(user);
        var refreshTokenStr = jwtService.GenerateRefreshToken(user.Id);

        // 기존 refresh token 삭제 후 재발급
        db.RefreshTokens.RemoveRange(db.RefreshTokens.Where(rt => rt.UserId == user.Id));
        db.RefreshTokens.Add(new RefreshToken
        {
            UserId = user.Id,
            User = user,
            Token = refreshTokenStr,
            ExpiresAt = jwtService.RefreshTokenExpiry
        });
        await db.SaveChangesAsync();

        return Ok(new TokenResponse(accessToken, refreshTokenStr, user.Role.ToString(), user.MustChangePassword));
    }

    /// <summary>
    /// Refresh Token으로 Access/Refresh Token을 재발급합니다 (Refresh Token도 함께 갱신 - rotation).
    /// </summary>
    /// <param name="req">현재 보유한 Refresh Token</param>
    /// <returns>새로 발급된 Access/Refresh Token, 역할, 비밀번호 변경 필요 여부</returns>
    [HttpPost("refresh")]
    public async Task<ActionResult<TokenResponse>> Refresh([FromBody] RefreshRequest req)
    {
        var stored = await db.RefreshTokens
            .Include(rt => rt.User)
            .FirstOrDefaultAsync(rt => rt.Token == req.RefreshToken);

        // DB에 없는(위조/이미 폐기된) 토큰이거나 만료된 토큰이면 재발급 거부
        if (stored is null || stored.ExpiresAt < DateTime.UtcNow)
            return Unauthorized("유효하지 않은 Refresh Token입니다.");

        var user = stored.User;
        var newAccess = jwtService.GenerateAccessToken(user);
        var newRefresh = jwtService.GenerateRefreshToken(user.Id);

        // rotation: 기존 토큰은 폐기하고 새 토큰으로 교체 (탈취된 토큰 재사용 방지)
        db.RefreshTokens.Remove(stored);
        db.RefreshTokens.Add(new RefreshToken
        {
            UserId = user.Id,
            User = user,
            Token = newRefresh,
            ExpiresAt = jwtService.RefreshTokenExpiry
        });
        await db.SaveChangesAsync();

        return Ok(new TokenResponse(newAccess, newRefresh, user.Role.ToString(), user.MustChangePassword));
    }

    /// <summary>
    /// 로그아웃 - 해당 사용자에게 발급된 Refresh Token을 전체 폐기합니다.
    /// </summary>
    /// <returns>본문 없는 204 No Content</returns>
    [Authorize]
    [HttpPost("logout")]
    public async Task<IActionResult> Logout()
    {
        var userId = long.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        db.RefreshTokens.RemoveRange(db.RefreshTokens.Where(rt => rt.UserId == userId));
        await db.SaveChangesAsync();
        return NoContent();
    }

    /// <summary>
    /// 본인 비밀번호 변경 - 현재 비밀번호 확인 후 교체. 관리자가 임시 비밀번호를 부여한 뒤
    /// (대원 신규 등록 / 비밀번호 재설정) MustChangePassword가 true인 계정이 주 대상이지만,
    /// 역할 구분 없이 로그인한 누구나 언제든 호출할 수 있다.
    /// </summary>
    /// <param name="req">현재 비밀번호와 새 비밀번호</param>
    /// <returns>본문 없는 204 No Content</returns>
    [Authorize]
    [HttpPatch("password")]
    public async Task<IActionResult> ChangePassword([FromBody] ChangePasswordRequest req)
    {
        var userId = long.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var user = await db.Users.FindAsync(userId);
        if (user is null) return NotFound();

        // 현재 비밀번호를 모르면(타인이 세션을 탈취한 경우 등) 변경을 막기 위한 확인 절차
        if (!BCrypt.Net.BCrypt.Verify(req.CurrentPassword, user.PasswordHash))
            return Unauthorized("현재 비밀번호가 올바르지 않습니다.");

        user.PasswordHash = BCrypt.Net.BCrypt.HashPassword(req.NewPassword);
        // 본인이 직접 비밀번호를 바꿨으므로 강제 변경 플래그 해제
        user.MustChangePassword = false;
        user.UpdatedAt = DateTime.UtcNow;
        await db.SaveChangesAsync();
        return NoContent();
    }
}
