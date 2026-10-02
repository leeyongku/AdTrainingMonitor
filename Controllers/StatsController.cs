// 통계 API - 부대 기간별 등급 분포

using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using TrainingMonitor.Data;

namespace TrainingMonitor.Controllers;

[ApiController]
[Route("api/stats")]
[Authorize(Roles = "ADMIN")]
public class StatsController(AppDbContext db) : ControllerBase
{
    /// <summary>
    /// 지정한 부대와 그 하위 부대 전체를 합산한 기간별 등급 분포 통계를 조회합니다.
    /// </summary>
    /// <param name="unitId">기준 부대 id (하위 부대 전체를 포함해서 집계)</param>
    /// <param name="from">집계 시작일 (포함)</param>
    /// <param name="to">집계 종료일 (포함)</param>
    /// <returns>인원 수, 기록 수, 전체 등급 분포, 종목별 등급 분포</returns>
    [HttpGet("unit")]
    public async Task<ActionResult<object>> UnitStats(
        [FromQuery] long unitId,
        [FromQuery] DateOnly from,
        [FromQuery] DateOnly to)
    {
        // 기준 부대 자신 + 모든 하위 부대의 id 집합 (부대 계층 전체 합산을 위함)
        var unitIds = await CollectSubtreeUnitIds(unitId);
        if (unitIds.Count == 0) return BadRequest("존재하지 않는 부대입니다.");

        // 대상 부대 소속 인원의, 지정 기간 내 측정 기록만 조회 (Session이 없는 기록은 기간 판단 불가하므로 제외)
        var records = await db.Records
            .Include(r => r.Session)
            .Include(r => r.User)
            .Include(r => r.Category)
            .Where(r => r.User.UnitId != null && unitIds.Contains(r.User.UnitId.Value) &&
                        r.Session != null &&
                        r.Session.MeasuredAt >= from &&
                        r.Session.MeasuredAt <= to)
            .ToListAsync();

        var totalRecords =  records
            .Where(r => r.Grade != null)
            .GroupBy(r => r.Grade!);


        // 종목 구분 없이, 등급이 매겨진 기록만 등급별 개수로 집계 (예: {"특급": 12, "1급": 5})
        var gradeDistribution = records
            .Where(r => r.Grade != null)
            .GroupBy(r => r.Grade!)
            .ToDictionary(g => g.Key, g => g.Count());

        // 종목별로 다시 등급별 개수를 집계한 중첩 딕셔너리.
        // 1) Category.Name으로 1차 그룹화 -> 2) 각 종목 그룹 안에서 등급이 매겨진 기록만 걸러
        //    등급으로 2차 그룹화 -> 3) 등급별 개수로 변환.
        // 결과 형태: { "팔굽혀펴기": { "특급": 12, "1급": 5 }, "오래달리기": { "특급": 3 } }
        var byCategory = records
            .GroupBy(r => r.Category.Name)
            .ToDictionary(g => g.Key, g => g
                .Where(r => r.Grade != null)
                .GroupBy(r => r.Grade!)
                .ToDictionary(x => x.Key, x => x.Count()));

        var memberCount = await db.Users.CountAsync(u => u.UnitId != null && unitIds.Contains(u.UnitId.Value) && u.IsActive);

        return Ok(new
        {
            memberCount,
            recordCount = records.Count,
            gradeDistribution,
            byCategory
        });
    }

    /// <summary>
    /// 지정한 부대 자신과 그 아래 모든 하위 부대의 id 목록을 BFS(너비 우선 탐색)로 모읍니다.
    /// </summary>
    /// <param name="rootUnitId">탐색을 시작할 최상위(기준) 부대 id</param>
    /// <returns>기준 부대를 포함한 하위 부대 전체 id 집합. 존재하지 않는 부대면 빈 집합.</returns>
    private async Task<HashSet<long>> CollectSubtreeUnitIds(long rootUnitId)
    {
        var units = await db.Units.ToListAsync();
        if (!units.Any(u => u.Id == rootUnitId)) return new HashSet<long>();

        // 부모 id -> 자식 부대 id 목록. 트리를 반복 탐색하기 위해 미리 인접 리스트 형태로 구성
        var childrenByParent = units
            .Where(u => u.ParentId.HasValue)
            .GroupBy(u => u.ParentId!.Value)
            .ToDictionary(g => g.Key, g => g.Select(u => u.Id).ToList());

        // 큐를 이용한 BFS: 기준 부대부터 시작해 자식을 하나씩 꺼내며 결과 집합에 추가
        var result = new HashSet<long> { rootUnitId };
        var queue = new Queue<long>();
        queue.Enqueue(rootUnitId);
        while (queue.Count > 0)
        {
            var current = queue.Dequeue();
            if (!childrenByParent.TryGetValue(current, out var children)) continue;
            foreach (var childId in children)
            {
                // HashSet.Add가 false를 반환하면 이미 방문한 id이므로 큐에 다시 넣지 않음 (순환 방지)
                if (result.Add(childId)) queue.Enqueue(childId);
            }
        }

        return result;
    }

    /// <summary>
    /// 특정 대원의 종목별 최고 기록과 가장 최근 등급을 조회합니다.
    /// </summary>
    /// <param name="userId">조회할 대원 id</param>
    /// <returns>종목별 최고 기록(BestValue)과 최신 등급(LatestGrade) 목록</returns>
    [HttpGet("personal/{userId}")]
    public async Task<ActionResult<object>> PersonalBest(long userId)
    {
        var best = await db.Records
            .Include(r => r.Category)
            .Where(r => r.UserId == userId)
            .GroupBy(r => r.Category.Name)
            .Select(g => new
            {
                Category = g.Key,
                BestValue = g.Min(r => r.Value),   // 달리기는 낮을수록 좋음
                LatestGrade = g.OrderByDescending(r => r.CreatedAt).First().Grade
            })
            .ToListAsync();

        return Ok(best);
    }
}
