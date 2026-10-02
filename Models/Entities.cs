// 훈련 모니터링 시스템 JPA 엔티티 클래스

using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace TrainingMonitor.Models.Entities;


// 흐름은: Entities.cs 등 엔티티 클래스 수정 → 
// dotnet ef migrations add <이름>으로 새 마이그레이션 파일 생성 → 
// 서버 시작 시(또는 dotnet ef database update) DB에 반영. 코드(C# 클래스)와 실제 DB 스키마를 버전 관리하듯 동기화해주는 역할

// =============================================
// 부대
// =============================================
[Table("units")]
public class Unit
{
    [Key] public long Id { get; set; }

    [Required, MaxLength(100)]
    public string Name { get; set; } = string.Empty;

    public long? ParentId { get; set; }

    [ForeignKey("ParentId")]
    public Unit? Parent { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}

// =============================================
// 사용자 (간부 + 대원)
// =============================================
public enum UserRole { ADMIN, MEMBER }

[Table("users")]
public class User
{
    [Key] public long Id { get; set; }

    [Required, MaxLength(20)]
    public string MilitaryId { get; set; } = string.Empty;   // 군번

    [Required, MaxLength(50)]
    public string Name { get; set; } = string.Empty;

    [MaxLength(20)]
    public string? Rank { get; set; }                         // 계급

    [Required]
    public UserRole Role { get; set; }

    public long? UnitId { get; set; }

    [ForeignKey("UnitId")]
    public Unit? Unit { get; set; }

    [Required]
    public string PasswordHash { get; set; } = string.Empty;

    // 다음 로그인 시 비밀번호 변경을 강제해야 하는지 여부. 대원 신규 등록(임시 비밀번호 부여) 또는
    // 관리자의 비밀번호 재설정 직후 true로 세팅되고, 본인이 직접 비밀번호를 바꾸면 false로 풀린다.
    public bool MustChangePassword { get; set; } = false;

    public bool IsActive { get; set; } = true;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

    public DateTime? LastLoginAt { get; set; }

    // 얼굴 사진 (선택, JPEG 바이너리 그대로 저장 — 목록 아이콘 표시용으로 작게 압축해서 올라온다)
    public byte[]? PhotoData { get; set; }
}

// =============================================
// 체력 측정 종목
// =============================================
[Table("categories")]
public class Category
{
    [Key] public long Id { get; set; }

    [Required, MaxLength(50)]
    public string Name { get; set; } = string.Empty;         // 예: 3km 달리기

    [Required, MaxLength(20)]
    public string Unit { get; set; } = string.Empty;         // 초 / 회 / m

    public string? Description { get; set; }

    public bool IsActive { get; set; } = true;
}

// =============================================
// 등급 기준표
// =============================================
[Table("grade_criteria")]
public class GradeCriteria
{
    [Key] public long Id { get; set; }

    public long CategoryId { get; set; }

    [ForeignKey("CategoryId")]
    public Category Category { get; set; } = null!;

    [MaxLength(1)]
    public string? Gender { get; set; }                      // M / F / null(공통)

    [MaxLength(20)]
    public string? RankGroup { get; set; }                   // 병사 / 부사관 / 장교

    [Required, MaxLength(10)]
    public string Grade { get; set; } = string.Empty;        // 특급 / 1급 / 2급 / 3급 / 불합격

    public double? MinValue { get; set; }
    public double? MaxValue { get; set; }

    public int SortOrder { get; set; }
}

// =============================================
// 측정 세션
// =============================================
[Table("measurement_sessions")]
public class MeasurementSession
{
    [Key] public long Id { get; set; }

    public long? UnitId { get; set; }

    [ForeignKey("UnitId")]
    public Unit? Unit { get; set; }

    public long? AdminId { get; set; }

    [ForeignKey("AdminId")]
    public User? Admin { get; set; }

    [Required]
    public DateOnly MeasuredAt { get; set; }

    [MaxLength(100)]
    public string? Location { get; set; }

    public string? Note { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}

// =============================================
// 체력 측정 결과 (핵심 테이블)
// =============================================
[Table("records")]
public class Record
{
    [Key] public long Id { get; set; }

    public long? SessionId { get; set; }

    [ForeignKey("SessionId")]
    public MeasurementSession? Session { get; set; }

    [Required]
    public long UserId { get; set; }

    [ForeignKey("UserId")]
    public User User { get; set; } = null!;

    [Required]
    public long CategoryId { get; set; }

    [ForeignKey("CategoryId")]
    public Category Category { get; set; } = null!;

    [Required]
    public double Value { get; set; }                        // 측정값

    [MaxLength(10)]
    public string? Grade { get; set; }                       // 자동 산출 등급

    public string? Note { get; set; }

    // 촬영한 측정 증빙 사진 (선택, JPEG 바이너리 그대로 저장)
    public byte[]? Photo { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}

// =============================================
// JWT Refresh Token
// =============================================
[Table("refresh_tokens")]
public class RefreshToken
{
    [Key] public long Id { get; set; }

    [Required]
    public long UserId { get; set; }

    [ForeignKey("UserId")]
    public User User { get; set; } = null!;

    [Required, MaxLength(512)]
    public string Token { get; set; } = string.Empty;

    [Required]
    public DateTime ExpiresAt { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}