using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace TrainingMonitor.Migrations
{
    /// <inheritdoc />
    public partial class AddRecordPhoto : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<byte[]>(
                name: "photo",
                table: "records",
                type: "bytea",
                nullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "photo",
                table: "records");
        }
    }
}
