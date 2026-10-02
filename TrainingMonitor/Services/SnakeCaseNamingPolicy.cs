// JSON 속성명을 snake_case로 변환 (Android 클라이언트 계약과 일치시키기 위함)

using System.Text;
using System.Text.Json;

namespace TrainingMonitor.Services;

public class SnakeCaseNamingPolicy : JsonNamingPolicy
{
    // 대문자 앞에 '_'를 삽입하고 소문자화 (예: MilitaryId -> military_id)
    public override string ConvertName(string name)
    {
        var sb = new StringBuilder();
        for (var i = 0; i < name.Length; i++)
        {
            var c = name[i];
            if (char.IsUpper(c))
            {
                if (i > 0) sb.Append('_');
                sb.Append(char.ToLowerInvariant(c));
            }
            else
            {
                sb.Append(c);
            }
        }
        return sb.ToString();
    }
}