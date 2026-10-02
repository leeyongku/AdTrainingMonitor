// 계급 목록과 계급→계급군(병사/부사관/장교) 매핑을 관리하는 단일 소스.
// RecordsController(등급 산출)와 MembersController(GET /api/ranks, 안드로이드 대원 추가 화면의
// 계급 스피너 데이터 소스)가 이 클래스를 공유해서, 클라이언트가 보낼 수 있는 계급 값과
// 서버의 등급 산출 로직이 항상 같은 목록을 기준으로 동작하도록 보장한다.

namespace TrainingMonitor.Models;

public static class RankCatalog
{
    // 등록 순서 = 안드로이드 스피너에 표시될 순서
    public static readonly IReadOnlyDictionary<string, string> RankGroups = new Dictionary<string, string>
    {
        ["이병"] = "병사",
        ["일병"] = "병사",
        ["상병"] = "병사",
        ["병장"] = "병사",
        ["하사"] = "부사관",
        ["중사"] = "부사관",
        ["상사"] = "부사관",
        ["원사"] = "부사관",
        ["소위"] = "장교",
        ["중위"] = "장교",
        ["대위"] = "장교",
        ["소령"] = "장교",
        ["중령"] = "장교",
        ["대령"] = "장교"
    };

    public static IReadOnlyList<string> AllRanks { get; } = RankGroups.Keys.ToList();
}
