// 종목별 등급 판정 기준표. grade_criteria DB 테이블을 대신해 이 정적 카탈로그를 단일 소스로 쓴다.
// RecordsController가 기록 저장 시 최종 등급 산출(CalculateGrade)과 안드로이드 기록 입력 화면의
// 등급 미리보기(GET /api/records/grade-criteria) 양쪽 모두 여기서 값을 가져오므로, 두 결과는
// 항상 일치한다.

namespace TrainingMonitor.Models;

public static class GradeCriteriaCatalog
{
    // RankGroup이 null이면 계급 구분 없이 공통으로 적용되는 기준. MinValue/MaxValue가 null이면
    // 해당 방향으로 제한이 없다는 뜻이다 (예: 불합격은 보통 양쪽 다 null).
    public record Entry(string? RankGroup, string Grade, double? MinValue, double? MaxValue);

    // categoryId -> 기준 목록. 앞쪽 항목일수록 우선 매칭되므로 좋은 등급부터 순서대로 나열해야 한다.
    public static readonly IReadOnlyDictionary<long, List<Entry>> ByCategory = new Dictionary<long, List<Entry>>
    {
        // 1: 3km 달리기 (초, 낮을수록 좋음) — 병사 기준
        [1] = new List<Entry>
        {
            new(RankGroup: "병사", Grade: "특급", MinValue: null, MaxValue: 720),
            new(RankGroup: "병사", Grade: "1급", MinValue: null, MaxValue: 780),
            new(RankGroup: "병사", Grade: "2급", MinValue: null, MaxValue: 900),
            new(RankGroup: "병사", Grade: "3급", MinValue: null, MaxValue: 960),
            new(RankGroup: "병사", Grade: "불합격", MinValue: null, MaxValue: null),
        },
        // 2: 팔굽혀펴기 (회, 높을수록 좋음)
        // TODO: 임시값이다. 실제 기준이 확정되면 교체할 것.
        [2] = new List<Entry>
        {
            new(RankGroup: "병사", Grade: "특급", MinValue: 70, MaxValue: null),
            new(RankGroup: "병사", Grade: "1급", MinValue: 60, MaxValue: null),
            new(RankGroup: "병사", Grade: "2급", MinValue: 50, MaxValue: null),
            new(RankGroup: "병사", Grade: "3급", MinValue: 40, MaxValue: null),
            new(RankGroup: "병사", Grade: "불합격", MinValue: null, MaxValue: null),
        },
        // 3: 윗몸일으키기 (회, 높을수록 좋음)
        // TODO: 임시값이다. 실제 기준이 확정되면 교체할 것.
        [3] = new List<Entry>
        {
            new(RankGroup: "병사", Grade: "특급", MinValue: 80, MaxValue: null),
            new(RankGroup: "병사", Grade: "1급", MinValue: 70, MaxValue: null),
            new(RankGroup: "병사", Grade: "2급", MinValue: 60, MaxValue: null),
            new(RankGroup: "병사", Grade: "3급", MinValue: 50, MaxValue: null),
            new(RankGroup: "병사", Grade: "불합격", MinValue: null, MaxValue: null),
        },
    };
}
