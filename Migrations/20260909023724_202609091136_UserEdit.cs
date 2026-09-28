using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace TrainingMonitor.Migrations
{
    /// <inheritdoc />
    public partial class _202609091136_UserEdit : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<DateTime>(
                name: "last_login_at",
                table: "users",
                type: "timestamp with time zone",
                nullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "last_login_at",
                table: "users");
        }
    }
}
