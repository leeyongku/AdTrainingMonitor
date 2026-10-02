using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

#pragma warning disable CA1814 // Prefer jagged arrays over multidimensional

namespace TrainingMonitor.Migrations
{
    /// <inheritdoc />
    public partial class SeedUnits : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.InsertData(
                table: "units",
                columns: new[] { "id", "created_at", "name", "parent_id" },
                values: new object[,]
                {
                    { 1L, new DateTime(2026, 1, 1, 0, 0, 0, 0, DateTimeKind.Utc), "1여단", null },
                    { 2L, new DateTime(2026, 1, 1, 0, 0, 0, 0, DateTimeKind.Utc), "1대대", 1L },
                    { 3L, new DateTime(2026, 1, 1, 0, 0, 0, 0, DateTimeKind.Utc), "1중대", 2L },
                    { 4L, new DateTime(2026, 1, 1, 0, 0, 0, 0, DateTimeKind.Utc), "1소대", 3L },
                    { 5L, new DateTime(2026, 1, 1, 0, 0, 0, 0, DateTimeKind.Utc), "1분대", 4L }
                });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DeleteData(
                table: "units",
                keyColumn: "id",
                keyValue: 5L);

            migrationBuilder.DeleteData(
                table: "units",
                keyColumn: "id",
                keyValue: 4L);

            migrationBuilder.DeleteData(
                table: "units",
                keyColumn: "id",
                keyValue: 3L);

            migrationBuilder.DeleteData(
                table: "units",
                keyColumn: "id",
                keyValue: 2L);

            migrationBuilder.DeleteData(
                table: "units",
                keyColumn: "id",
                keyValue: 1L);
        }
    }
}
