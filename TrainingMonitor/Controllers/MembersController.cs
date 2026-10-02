// 대원 관리 API (관리자 전용)

using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using System.Security.Claims;
using TrainingMonitor.Data;
using TrainingMonitor.Models;

namespace TrainingMonitor.Controllers;

[ApiController]
[Route("api/members")]
[Authorize(Roles = "ADMIN")]
public class MembersController(AppDbContext db) : ControllerBase
{
    public record MemberDto(long Id, string MilitaryId, string Name, string? Rank, string? UnitName, string Role, string? PhotoBase64);

    /// <summary>
    /// 소속 부대 대원 목록을 조회합니다. unitId를 지정하지 않으면 요청한 관리자의 소속 부대 기준입니다.
    /// </summary>
    /// <param name="unitId">조회할 부대 id (미지정 시 요청 관리자의 소속 부대)</param>
    /// <returns>대원 DTO 목록 (대상 부대가 없으면 빈 목록)</returns>
    [HttpGet]
    public async Task<ActionResult<List<MemberDto>>> GetMembers([FromQuery] long? unitId)
    {
        var adminUnitId = await GetAdminUnitId();
        var targetUnitId = unitId ?? adminUnitId;

        if (targetUnitId is null) return Ok(new List<MemberDto>());

        // 소속 부대명은 "1분대"처럼 최하위 이름만이 아니라, "1여단 1대대 1중대 1소대 1분대"처럼
        // 최상위 부대부터 이어붙인 전체 경로로 내려준다 (GetUnits()의 BuildPath와 동일한 방식).
        var unitPaths = await BuildUnitPathsAsync();

        var members = await db.Users
            .Where(u => u.IsActive)
            .ToListAsync();

        return Ok(members.Select(u => new MemberDto(
            u.Id, u.MilitaryId, u.Name, u.Rank,
            u.UnitId.HasValue && unitPaths.TryGetValue(u.UnitId.Value, out var path) ? path : null,
            u.Role.ToString(),
            u.PhotoData is null ? null : Convert.ToBase64String(u.PhotoData)
        )).ToList());
    }

    /// <summary>
    /// 부대 id별로, 최상위 부대부터 이어붙인 전체 경로("1여단 1대대 1중대")를 미리 계산해 둡니다.
    /// </summary>
    /// <returns>부대 id -> 전체 경로 문자열 딕셔너리</returns>
    private async Task<Dictionary<long, string>> BuildUnitPathsAsync()
    {
        var units = await db.Units.ToListAsync();
        var byId = units.ToDictionary(u => u.Id);

        // 해당 부대에서 부모를 따라 최상위까지 거슬러 올라가며 이름을 앞쪽에 삽입 -> 최상위부터 순서대로 나열됨
        string BuildPath(TrainingMonitor.Models.Entities.Unit unit)
        {
            var names = new List<string>();
            TrainingMonitor.Models.Entities.Unit? current = unit;
            while (current is not null)
            {
                names.Insert(0, current.Name);
                current = current.ParentId.HasValue && byId.TryGetValue(current.ParentId.Value, out var parent)
                    ? parent
                    : null;
            }
            return string.Join(" ", names);
        }

        return units.ToDictionary(u => u.Id, BuildPath);
    }

    /// <summary>
    /// 대원을 신규 등록합니다. 군번 중복은 허용하지 않으며, 비밀번호는 해시로 저장합니다.
    /// 계급과 소속 부대는 필수이며, 얼굴 사진은 선택입니다.
    /// </summary>
    /// <param name="req">군번, 이름, 초기 비밀번호, 계급, 소속 부대, 얼굴 사진(선택)</param>
    /// <returns>등록 성공 시 201 Created</returns>
    [HttpPost]
    public async Task<IActionResult> CreateMember([FromBody] CreateMemberRequest req)
    {
        if (string.IsNullOrWhiteSpace(req.Rank) || req.UnitId is null)
            return BadRequest("계급과 소속 부대는 필수입니다.");

        if (await db.Users.AnyAsync(u => u.MilitaryId == req.MilitaryId))
            return Conflict("이미 등록된 군번입니다.");

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

        var user = new TrainingMonitor.Models.Entities.User
        {
            MilitaryId = req.MilitaryId,
            Name = req.Name,
            Rank = req.Rank,
            Role = TrainingMonitor.Models.Entities.UserRole.MEMBER,
            UnitId = req.UnitId,
            PasswordHash = BCrypt.Net.BCrypt.HashPassword(req.Password),
            // 관리자가 정한 임시 비밀번호이므로, 다음 로그인 시 본인이 직접 바꾸도록 강제한다.
            MustChangePassword = true,
            PhotoData = photo
        };

        db.Users.Add(user);
        await db.SaveChangesAsync();
        return CreatedAtAction(nameof(GetMembers), new { id = user.Id }, null);
    }

    /// <summary>
    /// 대원의 이름/계급/소속 부대/얼굴 사진을 수정합니다. 군번과 비밀번호는 이 엔드포인트로
    /// 바꿀 수 없습니다 (군번은 로그인 식별자라 변경 불가, 비밀번호는 별도의 재설정 API를 사용).
    /// </summary>
    /// <param name="id">수정할 대원 id</param>
    /// <param name="req">이름, 계급, 소속 부대(모두 필수)와 얼굴 사진 변경 지시</param>
    /// <returns>대상이 없으면 404, 필수값 누락 시 400, 성공 시 204</returns>
    [HttpPatch("{id}")]
    public async Task<IActionResult> UpdateMember(long id, [FromBody] UpdateMemberRequest req)
    {
        if (string.IsNullOrWhiteSpace(req.Name) || string.IsNullOrWhiteSpace(req.Rank) || req.UnitId is null)
            return BadRequest("이름, 계급, 소속 부대는 필수입니다.");

        var user = await db.Users.FindAsync(id);
        if (user is null) return NotFound();

        // 사진 변경은 세 가지 경우로 나뉜다: 삭제 요청 / 새 사진 첨부 / 아무 지시 없음(기존 사진 유지).
        if (req.RemovePhoto)
        {
            user.PhotoData = null;
        }
        else if (req.PhotoBase64 is not null)
        {
            try
            {
                user.PhotoData = Convert.FromBase64String(req.PhotoBase64);
            }
            catch (FormatException)
            {
                return BadRequest("잘못된 사진 데이터입니다.");
            }
        }

        user.Name = req.Name;
        user.Rank = req.Rank;
        user.UnitId = req.UnitId;
        user.UpdatedAt = DateTime.UtcNow;
        await db.SaveChangesAsync();
        return NoContent();
    }

    /// <summary>
    /// 대원을 비활성화합니다 (제대/전역). 실제로 삭제하지 않고 IsActive만 false로 바꿉니다.
    /// 관리자(ADMIN) 계정은 이 기능으로 비활성화할 수 없습니다.
    /// </summary>
    /// <param name="id">비활성화할 대원 id</param>
    /// <returns>대상이 없으면 404, 관리자 계정이면 400, 성공 시 204</returns>
    [HttpDelete("{id}")]
    public async Task<IActionResult> DeactivateMember(long id)
    {
        var user = await db.Users.FindAsync(id);
        if (user is null) return NotFound();
        if (user.Role == TrainingMonitor.Models.Entities.UserRole.ADMIN) return BadRequest("관리자 계정은 이 기능으로 삭제할 수 없습니다.");

        user.IsActive = false;
        user.UpdatedAt = DateTime.UtcNow;
        await db.SaveChangesAsync();
        return NoContent();
    }

    /// <summary>
    /// 대원을 DB에서 완전히 삭제합니다 (복구 불가능). 외래키 제약(records.user_id) 때문에
    /// 먼저 그 대원의 측정 기록을 모두 지운 뒤 대원 자신을 삭제합니다.
    /// 관리자(ADMIN) 계정은 이 기능으로 삭제할 수 없습니다.
    /// </summary>
    /// <param name="id">완전 삭제할 대원 id</param>
    /// <returns>대상이 없으면 404, 관리자 계정이면 400, 성공 시 204</returns>
    [HttpDelete("{id}/permanent")]
    public async Task<IActionResult> DeleteMemberPermanently(long id)
    {
        var user = await db.Users.FindAsync(id);
        if (user is null) return NotFound();
        if (user.Role == TrainingMonitor.Models.Entities.UserRole.ADMIN) return BadRequest("관리자 계정은 이 기능으로 삭제할 수 없습니다.");

        var records = await db.Records.Where(r => r.UserId == id).ToListAsync();
        db.Records.RemoveRange(records);
        db.Users.Remove(user);
        await db.SaveChangesAsync();

        return NoContent();
    }

    /// <summary>
    /// 대원 비밀번호를 관리자가 대신 새 비밀번호로 재설정합니다.
    /// </summary>
    /// <param name="id">비밀번호를 재설정할 대원 id</param>
    /// <param name="req">새 비밀번호</param>
    /// <returns>대상이 없으면 404, 성공 시 204</returns>
    [HttpPatch("{id}/password")]
    public async Task<IActionResult> ResetPassword(long id, [FromBody] ResetPasswordRequest req)
    {
        var user = await db.Users.FindAsync(id);
        if (user is null) return NotFound();

        user.PasswordHash = BCrypt.Net.BCrypt.HashPassword(req.NewPassword);
        // 관리자가 정한 임시 비밀번호이므로, 다음 로그인 시 본인이 직접 바꾸도록 강제한다.
        user.MustChangePassword = true;
        user.UpdatedAt = DateTime.UtcNow;
        await db.SaveChangesAsync();
        return NoContent();
    }

    /// <summary>
    /// 등록 가능한 계급 목록을 조회합니다 (안드로이드 "대원 추가" 화면의 계급 스피너가 이 목록으로 채워짐 —
    /// RecordsController.CalculateGrade가 등급을 산출할 때 인식하는 계급과 항상 같은 목록).
    /// </summary>
    /// <returns>계급 문자열 목록</returns>
    [HttpGet("ranks")]
    public ActionResult<List<string>> GetRanks() => Ok(RankCatalog.AllRanks);


    // // 등록 가능한 소속 부대 목록 조회 (안드로이드 "대원 추가" 화면의 소속 부대 스피너가 이 목록으로 채워진다)
    //     // 이름은 "1여단 1대대 1중대"처럼 최상위 부대부터 이어붙인 전체 경로로 내려준다.
    //     [HttpGet("units")]
    //     public async Task<ActionResult<List<UnitDto>>> GetUnits()
    //     {
    //         var units = await db.Units.OrderBy(u => u.Id).ToListAsync();
    //         var byId = units.ToDictionary(u => u.Id);

    //         string BuildPath(TrainingMonitor.Models.Entities.Unit unit)
    //         {
    //             var names = new List<string>();
    //             TrainingMonitor.Models.Entities.Unit? current = unit;
    //             while (current is not null)
    //             {
    //                 names.Insert(0, current.Name);
    //                 current = current.ParentId.HasValue && byId.TryGetValue(current.ParentId.Value, out var parent)
    //                     ? parent
    //                     : null;
    //             }
    //             return string.Join(" ", names);
    //         }

    //         // 루트(최상위 여단)까지 거슬러 올라간 깊이. 0=여단, 1=대대, 2=중대, 3=소대, 4=분대.
    //         // 별도 레벨 컬럼 없이 부대 트리 깊이로 계산하며, 5단계보다 깊은 데이터는 분대(4)로 취급한다.
    //         int BuildLevel(TrainingMonitor.Models.Entities.Unit unit)
    //         {
    //             var depth = 0;
    //             TrainingMonitor.Models.Entities.Unit? current = unit;
    //             while (current!.ParentId.HasValue && byId.TryGetValue(current.ParentId.Value, out var parent))
    //             {
    //                 depth++;
    //                 current = parent;
    //             }
    //             return Math.Min(depth, 4);
    //         }

    //         return Ok(units.Select(u => new UnitDto(u.Id, BuildPath(u), BuildLevel(u))).ToList());
    //     }


    /// <summary>
    /// 등록 가능한 소속 부대 목록을 조회합니다 (안드로이드 "대원 추가" 화면의 소속 부대 스피너가 이 목록으로 채워짐).
    /// 이름은 "1여단 1대대 1중대"처럼 최상위 부대부터 이어붙인 전체 경로로 내려줍니다.
    /// </summary>
    /// <returns>부대 id, 전체 경로, 계층 레벨(0=여단 ~ 4=분대)을 담은 목록</returns>
    [HttpGet("units")]
    public async Task<ActionResult<List<UnitDto>>> GetUnits()
    {
        var units = await db.Units.OrderBy(u => u.Id).ToListAsync();
        var byId = units.ToDictionary(u => u.Id);

        // 한 번의 부모 순회로 전체 경로와 레벨을 동시에 계산
        // 이름은 "1여단 1대대 1중대"처럼 최상위부터 이어붙인다.
        // 레벨: 0=여단, 1=대대, 2=중대, 3=소대, 4=분대 (5단계 이상은 분대로 취급)
        (string Path, int Level) BuildPathAndLevel(TrainingMonitor.Models.Entities.Unit unit)
        {
            var names = new List<string>();
            var depth = 0;
            TrainingMonitor.Models.Entities.Unit? current = unit;

            while (current is not null)
            {
                names.Insert(0, current.Name);

                if (current.ParentId.HasValue && byId.TryGetValue(current.ParentId.Value, out var parent))
                {
                    depth++;
                    current = parent;
                }
                else
                {
                    current = null;
                }
            }

            return (string.Join(" ", names), Math.Min(depth, 4));
        }

        return Ok(
            units.Select(u =>
            {
              var (path, level) = BuildPathAndLevel(u);
              return new UnitDto(u.Id, path, level);
            })
            .ToList());
    }

    /// <summary>
    /// 요청한 관리자 본인의 소속 부대 id를 조회합니다.
    /// </summary>
    /// <returns>소속 부대가 없으면 null</returns>
    private async Task<long?> GetAdminUnitId()
    {
        var adminId = long.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)!);
        return (await db.Users.FindAsync(adminId))?.UnitId;
    }

    public record UnitDto(long Id, string Name, int Level);

    public record CreateMemberRequest(
        string MilitaryId,
        string Name,
        string Password,
        string? Rank,
        long? UnitId,
        string? PhotoBase64
    );

    public record ResetPasswordRequest(string NewPassword);

    public record UpdateMemberRequest(
        string Name,
        string? Rank,
        long? UnitId,
        string? PhotoBase64,
        bool RemovePhoto
    );
}
