using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace TrainingMonitor.Migrations
{
    /// <inheritdoc />
    public partial class AddMemberPhoto : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<byte[]>(
                name: "photo_data",
                table: "users",
                type: "bytea",
                nullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "photo_data",
                table: "users");
        }
    }
}
