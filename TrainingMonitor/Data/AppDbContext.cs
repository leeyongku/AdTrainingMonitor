// EF Core DbContext - DB 연결 및 테이블 매핑

using Microsoft.EntityFrameworkCore;
using TrainingMonitor.Models.Entities;

namespace TrainingMonitor.Data;

// 주 생성자로 DbContextOptions를 받아 base(DbContext)에 그대로 전달
// 연결 문자열/공급자(Npgsql) 설정은 Program.cs의 AddDbContext에서 주입됨
public class AppDbContext(DbContextOptions<AppDbContext> options) : DbContext(options)
{
    // 부대 (자기참조 트리 구조: ParentId)
    public DbSet<Unit> Units => Set<Unit>();
    // 사용자 (병사/관리자 등)
    public DbSet<User> Users => Set<User>();
    // 측정 종목 (3km 달리기, 팔굽혀펴기 등)
    public DbSet<Category> Categories => Set<Category>();
    // 종목별 등급 판정 기준
    public DbSet<GradeCriteria> GradeCriteria => Set<GradeCriteria>();
    // 측정 세션 (특정 일시/장소에 실시한 측정 회차)
    public DbSet<MeasurementSession> Sessions => Set<MeasurementSession>();
    // 개별 측정 기록 (세션 + 사용자 + 종목별 수치)
    public DbSet<Record> Records => Set<Record>();
    // 로그인 Refresh Token 저장소
    public DbSet<RefreshToken> RefreshTokens => Set<RefreshToken>();

    // 모델 관계, 제약조건, 시드 데이터 등 Fluent API로 정의하는 곳
    // 여기서 구성한 내용이 `dotnet ef migrations add`로 마이그레이션 파일에 반영됨
    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        // 군번 유니크 인덱스
        // 같은 군번으로 중복 가입/등록되는 것을 DB 레벨에서 방지
        modelBuilder.Entity<User>()
            .HasIndex(u => u.MilitaryId)
            .IsUnique();

        // 동일 세션에 같은 종목 중복 입력 방지
        // (세션, 사용자, 종목) 조합이 유일해야 함 — 한 사람이 같은 세션에서 같은 종목을 두 번 기록할 수 없음
        modelBuilder.Entity<Record>()
            .HasIndex(r => new { r.SessionId, r.UserId, r.CategoryId })
            .IsUnique();

        // RefreshToken - User 삭제 시 연쇄 삭제
        // 사용자가 삭제되면 그 사용자에게 발급된 토큰들도 자동으로 함께 삭제되어 고아 레코드 방지
        modelBuilder.Entity<RefreshToken>()
            .HasOne(rt => rt.User)
            .WithMany()
            .HasForeignKey(rt => rt.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        // UserRole enum → 문자열로 저장
        // 정수 대신 이름으로 저장해서 DB를 직접 조회할 때 가독성을 높이고, enum 순서 변경에도 안전하게 함
        modelBuilder.Entity<User>()
            .Property(u => u.Role)
            .HasConversion<string>();

        // 기초 데이터 (부대 계층 - 1여단 > 1대대 > 1중대 > 1소대 > 1분대)
        // CreatedAt은 HasData에서 결정론적 값이어야 하므로 고정 시각을 명시
        var unitSeedCreatedAt = new DateTime(2026, 1, 1, 0, 0, 0, DateTimeKind.Utc);
        modelBuilder.Entity<Unit>().HasData(
            new { Id = 1L, Name = "1여단", ParentId = (long?)null, CreatedAt = unitSeedCreatedAt },
            new { Id = 2L, Name = "1대대", ParentId = (long?)1L, CreatedAt = unitSeedCreatedAt },
            new { Id = 3L, Name = "1중대", ParentId = (long?)2L, CreatedAt = unitSeedCreatedAt },
            new { Id = 4L, Name = "1소대", ParentId = (long?)3L, CreatedAt = unitSeedCreatedAt },
            new { Id = 5L, Name = "1분대", ParentId = (long?)4L, CreatedAt = unitSeedCreatedAt }
        );

        // 기초 데이터 (측정 종목)
        // HasData는 마이그레이션 Up()의 InsertData로 변환되어 최초 배포 시 자동으로 시드됨
        modelBuilder.Entity<Category>().HasData(
            new Category { Id = 1, Name = "3km 달리기", Unit = "초", Description = "3km 구간 완주 시간 (초 단위)" },
            new Category { Id = 2, Name = "팔굽혀펴기",  Unit = "회", Description = "2분 내 최대 횟수" },
            new Category { Id = 3, Name = "윗몸일으키기", Unit = "회", Description = "2분 내 최대 횟수" }
        );

        // 등급 기준 예시 (3km 달리기, 병사 기준)
        // MaxValue만 있고 MinValue가 없는 것은 "이 값 이하면 해당 등급"이라는 구간 상한 기준 (마지막 불합격은 상한 없음)
        modelBuilder.Entity<GradeCriteria>().HasData(
            new GradeCriteria { Id = 1, CategoryId = 1, Gender = null, RankGroup = "병사", Grade = "특급",   MaxValue = 720,  SortOrder = 1 },
            new GradeCriteria { Id = 2, CategoryId = 1, Gender = null, RankGroup = "병사", Grade = "1급",    MaxValue = 780,  SortOrder = 2 },
            new GradeCriteria { Id = 3, CategoryId = 1, Gender = null, RankGroup = "병사", Grade = "2급",    MaxValue = 900,  SortOrder = 3 },
            new GradeCriteria { Id = 4, CategoryId = 1, Gender = null, RankGroup = "병사", Grade = "3급",    MaxValue = 960,  SortOrder = 4 },
            new GradeCriteria { Id = 5, CategoryId = 1, Gender = null, RankGroup = "병사", Grade = "불합격", MaxValue = null, SortOrder = 5 }
        );        
    }
}

