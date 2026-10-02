// 측정 세션 API (관리자 전용)

using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using System.Security.Claims;
using TrainingMonitor.Data;
using TrainingMonitor.Models.Entities;

namespace TrainingMonitor.Controllers;

[ApiController]
[Route("api/sessions")]
[Authorize(Roles = "ADMIN")]
public class SessionsController(AppDbContext db) : ControllerBase
{
    // 세션 생성 요청 바디. UnitId를 비워두면 요청한 관리자의 소속 부대로 자동 지정된다.
    public record SessionRequest(long? UnitId, DateOnly MeasuredAt, string? Location, string? Note);

    // 세션 목록 응답용 DTO. AdminName/UnitName은 Include로 가져온 연관 엔티티에서 꺼낸 값.
    public record SessionDto(long Id, DateOnly MeasuredAt, string? Location, string? AdminName, string? UnitName);

    /// <summary>
    /// 측정 세션을 생성합니다. UnitId를 지정하지 않으면 요청한 관리자의 소속 부대로 자동 설정됩니다.
    /// </summary>
    /// <param name="req">세션 생성 요청 (부대, 측정일, 장소, 메모)</param>
    /// <returns>생성된 세션의 id</returns>
    [HttpPost]
    public async Task<ActionResult<object>> CreateSession([FromBody] SessionRequest req)
    {
        // JWT의 NameIdentifier 클레임(로그인 시 발급한 사용자 id)으로 요청자(관리자)를 특정
        var adminId = long.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var admin = await db.Users.Include(u => u.Unit).FirstAsync(u => u.Id == adminId);

        // UnitId를 명시했으면 해당 부대, 아니면 관리자 본인의 소속 부대를 세션의 부대로 사용
        var unit = req.UnitId.HasValue
            ? await db.Units.FindAsync(req.UnitId.Value)
            : admin.Unit;

        var session = new MeasurementSession
        {
            Unit = unit,
            Admin = admin,
            MeasuredAt = req.MeasuredAt,
            Location = req.Location,
            Note = req.Note
        };

        db.Sessions.Add(session);
        await db.SaveChangesAsync();
        return Ok(new { sessionId = session.Id });
    }

    /// <summary>
    /// 요청한 관리자 소속 부대의 측정 세션 목록을 최신 측정일 순으로 조회합니다.
    /// </summary>
    /// <returns>세션 DTO 목록 (소속 부대가 없으면 빈 목록)</returns>
    [HttpGet]
    public async Task<ActionResult<List<SessionDto>>> GetSessions()
    {
        var adminId = long.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var unitId = (await db.Users.FindAsync(adminId))?.UnitId;
        // 관리자가 어느 부대에도 속해있지 않으면 조회할 세션이 없으므로 빈 목록으로 조기 반환
        if (unitId is null) return Ok(new List<SessionDto>());

        var sessions = await db.Sessions
            .Include(s => s.Admin)
            .Include(s => s.Unit)
            .Where(s => s.UnitId == unitId)
            .OrderByDescending(s => s.MeasuredAt)
            .Select(s => new SessionDto(s.Id, s.MeasuredAt, s.Location, s.Admin!.Name, s.Unit!.Name))
            .ToListAsync();

        return Ok(sessions);
    }
}
