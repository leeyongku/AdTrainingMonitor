// 체력 측정 기록 입력/조회 API

using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using System.Security.Claims;
using TrainingMonitor.Data;
using TrainingMonitor.Models;
using TrainingMonitor.Models.Entities;

namespace TrainingMonitor.Controllers;

[ApiController]
[Route("api/records")]
[Authorize]
public class RecordsController(AppDbContext db) : ControllerBase
{
    public record RecordRequest(long? SessionId, long UserId, long CategoryId, double Value, string? Note, string? PhotoBase64);
    public record RecordDto(long Id, string UserName, string CategoryName, double Value, string Unit, string? Grade, DateOnly? MeasuredAt, bool HasPhoto);
    public record TrendPoint(DateOnly MeasuredAt, double Value, string? Grade);
    public record GradeCriteriaDto(string Grade, double? MinValue, double? MaxValue);

    /// <summary>
    /// 측정 기록을 입력합니다 (관리자 전용). 등급을 자동 산출하고, 동일 세션·종목 중복 입력을 막습니다.
    /// </summary>
    /// <param name="req">기록 대상(세션/대원/종목), 측정값, 메모, 사진(Base64, 선택)</param>
    /// <returns>생성된 기록 id와 산출된 등급</returns>
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
                // Base64 디코딩 실패 = 클라이언트가 보낸 사진 데이터가 손상되었거나 형식이 틀림
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

    /// <summary>
    /// 로그인한 본인의 측정 기록을 조회합니다.
    /// </summary>
    /// <returns>최신순으로 정렬된 본인 기록 목록</returns>
    [HttpGet("my")]
    public async Task<ActionResult<List<RecordDto>>> MyRecords()
    {
        var userId = long.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        return Ok(await GetRecordDtos(r => r.UserId == userId));
    }

    /// <summary>
    /// 로그인한 본인의 측정 기록을 전부 삭제합니다. 관리자 전용 전체 삭제(DeleteAllUserRecords)와
    /// 달리 대상이 항상 본인(JWT의 NameIdentifier)으로 고정되므로 ADMIN 권한이 필요 없다.
    /// </summary>
    /// <returns>삭제된 기록 개수</returns>
    [HttpDelete("my")]
    public async Task<IActionResult> DeleteMyRecords()
    {
        var userId = long.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var records = await db.Records.Where(r => r.UserId == userId).ToListAsync();
        db.Records.RemoveRange(records);
        await db.SaveChangesAsync();
        return Ok(new { deletedCount = records.Count });
    }

    /// <summary>
    /// 로그인한 본인의 특정 종목 측정 기록을 전부 삭제합니다.
    /// </summary>
    /// <param name="categoryId">삭제할 종목 id</param>
    /// <returns>삭제된 기록 개수</returns>
    [HttpDelete("my/category/{categoryId}")]
    public async Task<IActionResult> DeleteMyRecordsByCategory(long categoryId)
    {
        var userId = long.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var records = await db.Records.Where(r => r.UserId == userId && r.CategoryId == categoryId).ToListAsync();
        db.Records.RemoveRange(records);
        await db.SaveChangesAsync();
        return Ok(new { deletedCount = records.Count });
    }

    /// <summary>
    /// 특정 대원의 측정 기록을 조회합니다 (관리자 전용).
    /// </summary>
    /// <param name="userId">조회할 대원 id</param>
    /// <returns>최신순으로 정렬된 해당 대원의 기록 목록</returns>
    [HttpGet("user/{userId}")]
    [Authorize(Roles = "ADMIN")]
    public async Task<ActionResult<List<RecordDto>>> UserRecords(long userId) =>
        Ok(await GetRecordDtos(r => r.UserId == userId));

    /// <summary>
    /// 특정 세션에서 입력된 전체 기록을 조회합니다 (관리자 전용).
    /// </summary>
    /// <param name="sessionId">조회할 측정 세션 id</param>
    /// <returns>최신순으로 정렬된 해당 세션의 기록 목록</returns>
    [HttpGet("session/{sessionId}")]
    [Authorize(Roles = "ADMIN")]
    public async Task<ActionResult<List<RecordDto>>> SessionRecords(long sessionId) =>
        Ok(await GetRecordDtos(r => r.SessionId == sessionId));

    /// <summary>
    /// 측정 기록 하나를 삭제합니다 (관리자 전용).
    /// </summary>
    /// <param name="id">삭제할 기록 id</param>
    /// <returns>대상이 없으면 404, 성공 시 204</returns>
    [HttpDelete("{id}")]
    [Authorize(Roles = "ADMIN")]
    public async Task<IActionResult> DeleteRecord(long id)
    {
        var record = await db.Records.FindAsync(id);
        if (record is null) return NotFound();

        db.Records.Remove(record);
        await db.SaveChangesAsync();
        return NoContent();
    }

    /// <summary>
    /// 특정 대원의 측정 기록을 전부 삭제합니다 (관리자 전용).
    /// </summary>
    /// <param name="userId">기록을 전부 삭제할 대원 id</param>
    /// <returns>삭제된 기록 개수</returns>
    [HttpDelete("user/{userId}")]
    [Authorize(Roles = "ADMIN")]
    public async Task<IActionResult> DeleteAllUserRecords(long userId)
    {
        var records = await db.Records.Where(r => r.UserId == userId).ToListAsync();
        db.Records.RemoveRange(records);
        await db.SaveChangesAsync();
        return Ok(new { deletedCount = records.Count });
    }

    /// <summary>
    /// 대원·종목 기준 등급 판정표를 조회합니다 (안드로이드 기록 입력 화면의 등급 미리보기가 사용).
    /// CalculateGrade와 동일하게 대원의 계급 -> 계급군을 반영한 기준표를 카탈로그 순서 그대로 내려줍니다.
    /// </summary>
    /// <param name="categoryId">기준표를 조회할 종목 id</param>
    /// <param name="userId">계급군 판단에 쓸 대원 id</param>
    /// <returns>등급별 최소/최대 기준값 목록</returns>
    [HttpGet("grade-criteria")]
    [Authorize(Roles = "ADMIN")]
    public async Task<ActionResult<List<GradeCriteriaDto>>> GradeCriteria(
        [FromQuery] long categoryId,
        [FromQuery] long userId)
    {
        var user = await db.Users.FindAsync(userId);
        if (user is null) return NotFound();

        var criteria = ResolveCriteriaList(categoryId, user.Rank);
        return Ok(criteria.Select(c => new GradeCriteriaDto(c.Grade, c.MinValue, c.MaxValue)).ToList());
    }

    /// <summary>
    /// 기록에 첨부된 사진 원본을 내려줍니다. 본인 기록이거나 관리자만 조회할 수 있습니다.
    /// </summary>
    /// <param name="id">사진을 조회할 기록 id</param>
    /// <returns>JPEG 이미지 바이너리 (기록/사진이 없으면 404, 권한 없으면 403)</returns>
    [HttpGet("{id}/photo")]
    public async Task<IActionResult> GetPhoto(long id)
    {
        var record = await db.Records.FindAsync(id);
        if (record is null || record.Photo is null) return NotFound();

        // 관리자가 아니면 본인 기록의 사진만 조회 가능 (타인의 사진 무단 열람 방지)
        if (!User.IsInRole("ADMIN"))
        {
            var requesterId = long.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
            if (record.UserId != requesterId) return Forbid();
        }

        return File(record.Photo, "image/jpeg");
    }

    /// <summary>
    /// 특정 종목의 기록 추이(그래프용)를 조회합니다. userId를 지정하지 않으면 요청한 본인 기준입니다.
    /// </summary>
    /// <param name="userId">조회할 대원 id (미지정 시 본인)</param>
    /// <param name="categoryId">조회할 종목 id</param>
    /// <returns>측정일 오름차순으로 정렬된 (측정일, 값, 등급) 포인트 목록</returns>
    [HttpGet("trend")]
    public async Task<ActionResult<List<TrendPoint>>> Trend(
        [FromQuery] long? userId,
        [FromQuery] long categoryId)
    {
        var targetId = userId ?? long.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);

        var points = await db.Records
            .Include(r => r.Session)
            .Where(r => r.UserId == targetId && r.CategoryId == categoryId && r.Session != null)
            .OrderBy(r => r.Session!.MeasuredAt)
            .Select(r => new TrendPoint(r.Session!.MeasuredAt, r.Value, r.Grade))
            .ToListAsync();

        return Ok(points);
    }

    // =============================================
    // 내부 메서드
    // =============================================

    /// <summary>
    /// 주어진 조건(predicate)에 맞는 기록을 최신순으로 조회해 DTO 목록으로 변환합니다.
    /// MyRecords/UserRecords/SessionRecords가 조회 조건만 다르게 이 메서드를 공유합니다.
    /// </summary>
    /// <param name="predicate">기록을 필터링할 조건식</param>
    /// <returns>변환된 기록 DTO 목록</returns>
    private async Task<List<RecordDto>> GetRecordDtos(
        System.Linq.Expressions.Expression<Func<Record, bool>> predicate)
    {
        return await db.Records
            .Include(r => r.User)
            .Include(r => r.Category)
            .Include(r => r.Session)
            .Where(predicate)
            .OrderByDescending(r => r.CreatedAt)
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
            .ToListAsync();
    }

    /// <summary>
    /// 계급을 병사/부사관/장교 그룹으로 매핑한 뒤 해당 그룹 기준으로 등급을 판정합니다.
    /// 그룹 전용 기준이 없으면 공통(null) 기준을 사용합니다.
    /// </summary>
    /// <param name="categoryId">등급을 판정할 종목 id</param>
    /// <param name="value">측정값</param>
    /// <param name="rank">대원의 계급 (그룹 매핑에 사용)</param>
    /// <returns>측정값이 속하는 등급 문자열. 기준에 해당하는 등급이 없으면 null.</returns>
    private string? CalculateGrade(long categoryId, double value, string? rank)
    {
        var criteria = ResolveCriteriaList(categoryId, rank);

        return criteria.FirstOrDefault(c =>
            (c.MinValue == null || value >= c.MinValue) &&
            (c.MaxValue == null || value <= c.MaxValue))?.Grade;
    }

    /// <summary>
    /// 종목 + 계급 기준으로 적용할 등급 기준표를 찾습니다. CalculateGrade(저장 시 최종 등급 산출)와
    /// GradeCriteria 엔드포인트(입력 화면의 등급 미리보기)가 이 선택 로직을 공유합니다.
    /// DB의 grade_criteria 테이블 대신 GradeCriteriaCatalog(하드코딩된 정적 기준표)를 단일 소스로 씁니다.
    /// 계급→그룹 매핑은 RankCatalog(안드로이드 대원 추가 화면의 계급 스피너와 공유하는 GET /api/members/ranks의 출처)를 그대로 사용합니다.
    /// </summary>
    /// <param name="categoryId">기준표를 찾을 종목 id</param>
    /// <param name="rank">계급 (그룹 매핑 후 그룹 전용 기준을 우선 탐색)</param>
    /// <returns>해당 종목·계급군에 적용할 등급 기준 목록. 그룹 전용 기준이 없으면 공통 기준으로 대체.</returns>
    private List<GradeCriteriaCatalog.Entry> ResolveCriteriaList(long categoryId, string? rank)
    {
        if (!GradeCriteriaCatalog.ByCategory.TryGetValue(categoryId, out var entries))
            return new List<GradeCriteriaCatalog.Entry>();

        var rankGroup = rank is not null && RankCatalog.RankGroups.TryGetValue(rank, out var group) ? group : null;

        // 계급군 전용 기준을 먼저 찾고, 없으면(그룹 미지정이거나 해당 그룹 기준이 없으면) 공통(null) 기준으로 대체
        var criteria = entries.Where(c => c.RankGroup == rankGroup).ToList();
        if (criteria.Count == 0)
            criteria = entries.Where(c => c.RankGroup == null).ToList();

        return criteria;
    }
}
