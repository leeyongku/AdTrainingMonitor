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

    // 기록 입력 (관리자) - 등급 자동 산출 및 동일 세션·종목 중복 방지 포함
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

    // 내 기록 조회 (대원 본인)
    [HttpGet("my")]
    public async Task<ActionResult<List<RecordDto>>> MyRecords()
    {
        var userId = long.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        return Ok(await GetRecordDtos(r => r.UserId == userId));
    }

    // 특정 대원 기록 조회 (관리자)
    [HttpGet("user/{userId}")]
    [Authorize(Roles = "ADMIN")]
    public async Task<ActionResult<List<RecordDto>>> UserRecords(long userId) =>
        Ok(await GetRecordDtos(r => r.UserId == userId));

    // 세션 전체 기록 조회
    [HttpGet("session/{sessionId}")]
    [Authorize(Roles = "ADMIN")]
    public async Task<ActionResult<List<RecordDto>>> SessionRecords(long sessionId) =>
        Ok(await GetRecordDtos(r => r.SessionId == sessionId));

    // 대원·종목 기준 등급 판정표 조회 (안드로이드 기록 입력 화면의 등급 미리보기가 사용).
    // CalculateGrade와 동일하게 대원의 계급 -> 계급군을 반영한 기준표를 카탈로그 순서 그대로 내려준다.
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

    // 추이 조회 (그래프용) - userId 미지정 시 요청한 본인 기준
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

    // 계급을 병사/부사관/장교 그룹으로 매핑 후 해당 그룹 기준으로 등급 판정, 그룹 기준이 없으면 공통(null) 기준 사용
    private string? CalculateGrade(long categoryId, double value, string? rank)
    {
        var criteria = ResolveCriteriaList(categoryId, rank);

        return criteria.FirstOrDefault(c =>
            (c.MinValue == null || value >= c.MinValue) &&
            (c.MaxValue == null || value <= c.MaxValue))?.Grade;
    }

    // 종목 + 계급 기준으로 적용할 등급 기준표를 찾는다. CalculateGrade(저장 시 최종 등급 산출)와
    // GradeCriteria 엔드포인트(입력 화면의 등급 미리보기)가 이 선택 로직을 공유한다.
    // DB의 grade_criteria 테이블 대신 GradeCriteriaCatalog(하드코딩된 정적 기준표)를 단일 소스로 쓴다.
    // 계급→그룹 매핑은 RankCatalog(안드로이드 대원 추가 화면의 계급 스피너와 공유하는 GET /api/members/ranks의 출처)를 그대로 사용한다.
    private List<GradeCriteriaCatalog.Entry> ResolveCriteriaList(long categoryId, string? rank)
    {
        if (!GradeCriteriaCatalog.ByCategory.TryGetValue(categoryId, out var entries))
            return new List<GradeCriteriaCatalog.Entry>();

        var rankGroup = rank is not null && RankCatalog.RankGroups.TryGetValue(rank, out var group) ? group : null;

        var criteria = entries.Where(c => c.RankGroup == rankGroup).ToList();
        if (criteria.Count == 0)
            criteria = entries.Where(c => c.RankGroup == null).ToList();

        return criteria;
    }
}