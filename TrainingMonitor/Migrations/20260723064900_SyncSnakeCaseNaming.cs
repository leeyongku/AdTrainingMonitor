using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace TrainingMonitor.Migrations
{
    /// <inheritdoc />
    public partial class SyncSnakeCaseNaming : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_grade_criteria_categories_CategoryId",
                table: "grade_criteria");

            migrationBuilder.DropForeignKey(
                name: "FK_measurement_sessions_units_UnitId",
                table: "measurement_sessions");

            migrationBuilder.DropForeignKey(
                name: "FK_measurement_sessions_users_AdminId",
                table: "measurement_sessions");

            migrationBuilder.DropForeignKey(
                name: "FK_records_categories_CategoryId",
                table: "records");

            migrationBuilder.DropForeignKey(
                name: "FK_records_measurement_sessions_SessionId",
                table: "records");

            migrationBuilder.DropForeignKey(
                name: "FK_records_users_UserId",
                table: "records");

            migrationBuilder.DropForeignKey(
                name: "FK_refresh_tokens_users_UserId",
                table: "refresh_tokens");

            migrationBuilder.DropForeignKey(
                name: "FK_units_units_ParentId",
                table: "units");

            migrationBuilder.DropForeignKey(
                name: "FK_users_units_UnitId",
                table: "users");

            migrationBuilder.DropPrimaryKey(
                name: "PK_users",
                table: "users");

            migrationBuilder.DropPrimaryKey(
                name: "PK_units",
                table: "units");

            migrationBuilder.DropPrimaryKey(
                name: "PK_refresh_tokens",
                table: "refresh_tokens");

            migrationBuilder.DropPrimaryKey(
                name: "PK_records",
                table: "records");

            migrationBuilder.DropPrimaryKey(
                name: "PK_measurement_sessions",
                table: "measurement_sessions");

            migrationBuilder.DropPrimaryKey(
                name: "PK_grade_criteria",
                table: "grade_criteria");

            migrationBuilder.DropPrimaryKey(
                name: "PK_categories",
                table: "categories");

            migrationBuilder.RenameColumn(
                name: "Role",
                table: "users",
                newName: "role");

            migrationBuilder.RenameColumn(
                name: "Rank",
                table: "users",
                newName: "rank");

            migrationBuilder.RenameColumn(
                name: "Name",
                table: "users",
                newName: "name");

            migrationBuilder.RenameColumn(
                name: "Id",
                table: "users",
                newName: "id");

            migrationBuilder.RenameColumn(
                name: "UpdatedAt",
                table: "users",
                newName: "updated_at");

            migrationBuilder.RenameColumn(
                name: "UnitId",
                table: "users",
                newName: "unit_id");

            migrationBuilder.RenameColumn(
                name: "PasswordHash",
                table: "users",
                newName: "password_hash");

            migrationBuilder.RenameColumn(
                name: "MilitaryId",
                table: "users",
                newName: "military_id");

            migrationBuilder.RenameColumn(
                name: "IsActive",
                table: "users",
                newName: "is_active");

            migrationBuilder.RenameColumn(
                name: "CreatedAt",
                table: "users",
                newName: "created_at");

            migrationBuilder.RenameIndex(
                name: "IX_users_UnitId",
                table: "users",
                newName: "ix_users_unit_id");

            migrationBuilder.RenameIndex(
                name: "IX_users_MilitaryId",
                table: "users",
                newName: "ix_users_military_id");

            migrationBuilder.RenameColumn(
                name: "Name",
                table: "units",
                newName: "name");

            migrationBuilder.RenameColumn(
                name: "Id",
                table: "units",
                newName: "id");

            migrationBuilder.RenameColumn(
                name: "ParentId",
                table: "units",
                newName: "parent_id");

            migrationBuilder.RenameColumn(
                name: "CreatedAt",
                table: "units",
                newName: "created_at");

            migrationBuilder.RenameIndex(
                name: "IX_units_ParentId",
                table: "units",
                newName: "ix_units_parent_id");

            migrationBuilder.RenameColumn(
                name: "Token",
                table: "refresh_tokens",
                newName: "token");

            migrationBuilder.RenameColumn(
                name: "Id",
                table: "refresh_tokens",
                newName: "id");

            migrationBuilder.RenameColumn(
                name: "UserId",
                table: "refresh_tokens",
                newName: "user_id");

            migrationBuilder.RenameColumn(
                name: "ExpiresAt",
                table: "refresh_tokens",
                newName: "expires_at");

            migrationBuilder.RenameColumn(
                name: "CreatedAt",
                table: "refresh_tokens",
                newName: "created_at");

            migrationBuilder.RenameIndex(
                name: "IX_refresh_tokens_UserId",
                table: "refresh_tokens",
                newName: "ix_refresh_tokens_user_id");

            migrationBuilder.RenameColumn(
                name: "Value",
                table: "records",
                newName: "value");

            migrationBuilder.RenameColumn(
                name: "Note",
                table: "records",
                newName: "note");

            migrationBuilder.RenameColumn(
                name: "Grade",
                table: "records",
                newName: "grade");

            migrationBuilder.RenameColumn(
                name: "Id",
                table: "records",
                newName: "id");

            migrationBuilder.RenameColumn(
                name: "UserId",
                table: "records",
                newName: "user_id");

            migrationBuilder.RenameColumn(
                name: "SessionId",
                table: "records",
                newName: "session_id");

            migrationBuilder.RenameColumn(
                name: "CreatedAt",
                table: "records",
                newName: "created_at");

            migrationBuilder.RenameColumn(
                name: "CategoryId",
                table: "records",
                newName: "category_id");

            migrationBuilder.RenameIndex(
                name: "IX_records_UserId",
                table: "records",
                newName: "ix_records_user_id");

            migrationBuilder.RenameIndex(
                name: "IX_records_SessionId_UserId_CategoryId",
                table: "records",
                newName: "ix_records_session_id_user_id_category_id");

            migrationBuilder.RenameIndex(
                name: "IX_records_CategoryId",
                table: "records",
                newName: "ix_records_category_id");

            migrationBuilder.RenameColumn(
                name: "Note",
                table: "measurement_sessions",
                newName: "note");

            migrationBuilder.RenameColumn(
                name: "Location",
                table: "measurement_sessions",
                newName: "location");

            migrationBuilder.RenameColumn(
                name: "Id",
                table: "measurement_sessions",
                newName: "id");

            migrationBuilder.RenameColumn(
                name: "UnitId",
                table: "measurement_sessions",
                newName: "unit_id");

            migrationBuilder.RenameColumn(
                name: "MeasuredAt",
                table: "measurement_sessions",
                newName: "measured_at");

            migrationBuilder.RenameColumn(
                name: "CreatedAt",
                table: "measurement_sessions",
                newName: "created_at");

            migrationBuilder.RenameColumn(
                name: "AdminId",
                table: "measurement_sessions",
                newName: "admin_id");

            migrationBuilder.RenameIndex(
                name: "IX_measurement_sessions_UnitId",
                table: "measurement_sessions",
                newName: "ix_measurement_sessions_unit_id");

            migrationBuilder.RenameIndex(
                name: "IX_measurement_sessions_AdminId",
                table: "measurement_sessions",
                newName: "ix_measurement_sessions_admin_id");

            migrationBuilder.RenameColumn(
                name: "Grade",
                table: "grade_criteria",
                newName: "grade");

            migrationBuilder.RenameColumn(
                name: "Gender",
                table: "grade_criteria",
                newName: "gender");

            migrationBuilder.RenameColumn(
                name: "Id",
                table: "grade_criteria",
                newName: "id");

            migrationBuilder.RenameColumn(
                name: "SortOrder",
                table: "grade_criteria",
                newName: "sort_order");

            migrationBuilder.RenameColumn(
                name: "RankGroup",
                table: "grade_criteria",
                newName: "rank_group");

            migrationBuilder.RenameColumn(
                name: "MinValue",
                table: "grade_criteria",
                newName: "min_value");

            migrationBuilder.RenameColumn(
                name: "MaxValue",
                table: "grade_criteria",
                newName: "max_value");

            migrationBuilder.RenameColumn(
                name: "CategoryId",
                table: "grade_criteria",
                newName: "category_id");

            migrationBuilder.RenameIndex(
                name: "IX_grade_criteria_CategoryId",
                table: "grade_criteria",
                newName: "ix_grade_criteria_category_id");

            migrationBuilder.RenameColumn(
                name: "Unit",
                table: "categories",
                newName: "unit");

            migrationBuilder.RenameColumn(
                name: "Name",
                table: "categories",
                newName: "name");

            migrationBuilder.RenameColumn(
                name: "Description",
                table: "categories",
                newName: "description");

            migrationBuilder.RenameColumn(
                name: "Id",
                table: "categories",
                newName: "id");

            migrationBuilder.RenameColumn(
                name: "IsActive",
                table: "categories",
                newName: "is_active");

            migrationBuilder.AddPrimaryKey(
                name: "pk_users",
                table: "users",
                column: "id");

            migrationBuilder.AddPrimaryKey(
                name: "pk_units",
                table: "units",
                column: "id");

            migrationBuilder.AddPrimaryKey(
                name: "pk_refresh_tokens",
                table: "refresh_tokens",
                column: "id");

            migrationBuilder.AddPrimaryKey(
                name: "pk_records",
                table: "records",
                column: "id");

            migrationBuilder.AddPrimaryKey(
                name: "pk_measurement_sessions",
                table: "measurement_sessions",
                column: "id");

            migrationBuilder.AddPrimaryKey(
                name: "pk_grade_criteria",
                table: "grade_criteria",
                column: "id");

            migrationBuilder.AddPrimaryKey(
                name: "pk_categories",
                table: "categories",
                column: "id");

            migrationBuilder.AddForeignKey(
                name: "fk_grade_criteria_categories_category_id",
                table: "grade_criteria",
                column: "category_id",
                principalTable: "categories",
                principalColumn: "id",
                onDelete: ReferentialAction.Cascade);

            migrationBuilder.AddForeignKey(
                name: "fk_measurement_sessions_units_unit_id",
                table: "measurement_sessions",
                column: "unit_id",
                principalTable: "units",
                principalColumn: "id");

            migrationBuilder.AddForeignKey(
                name: "fk_measurement_sessions_users_admin_id",
                table: "measurement_sessions",
                column: "admin_id",
                principalTable: "users",
                principalColumn: "id");

            migrationBuilder.AddForeignKey(
                name: "fk_records_categories_category_id",
                table: "records",
                column: "category_id",
                principalTable: "categories",
                principalColumn: "id",
                onDelete: ReferentialAction.Cascade);

            migrationBuilder.AddForeignKey(
                name: "fk_records_measurement_sessions_session_id",
                table: "records",
                column: "session_id",
                principalTable: "measurement_sessions",
                principalColumn: "id");

            migrationBuilder.AddForeignKey(
                name: "fk_records_users_user_id",
                table: "records",
                column: "user_id",
                principalTable: "users",
                principalColumn: "id",
                onDelete: ReferentialAction.Cascade);

            migrationBuilder.AddForeignKey(
                name: "fk_refresh_tokens_users_user_id",
                table: "refresh_tokens",
                column: "user_id",
                principalTable: "users",
                principalColumn: "id",
                onDelete: ReferentialAction.Cascade);

            migrationBuilder.AddForeignKey(
                name: "fk_units_units_parent_id",
                table: "units",
                column: "parent_id",
                principalTable: "units",
                principalColumn: "id");

            migrationBuilder.AddForeignKey(
                name: "fk_users_units_unit_id",
                table: "users",
                column: "unit_id",
                principalTable: "units",
                principalColumn: "id");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "fk_grade_criteria_categories_category_id",
                table: "grade_criteria");

            migrationBuilder.DropForeignKey(
                name: "fk_measurement_sessions_units_unit_id",
                table: "measurement_sessions");

            migrationBuilder.DropForeignKey(
                name: "fk_measurement_sessions_users_admin_id",
                table: "measurement_sessions");

            migrationBuilder.DropForeignKey(
                name: "fk_records_categories_category_id",
                table: "records");

            migrationBuilder.DropForeignKey(
                name: "fk_records_measurement_sessions_session_id",
                table: "records");

            migrationBuilder.DropForeignKey(
                name: "fk_records_users_user_id",
                table: "records");

            migrationBuilder.DropForeignKey(
                name: "fk_refresh_tokens_users_user_id",
                table: "refresh_tokens");

            migrationBuilder.DropForeignKey(
                name: "fk_units_units_parent_id",
                table: "units");

            migrationBuilder.DropForeignKey(
                name: "fk_users_units_unit_id",
                table: "users");

            migrationBuilder.DropPrimaryKey(
                name: "pk_users",
                table: "users");

            migrationBuilder.DropPrimaryKey(
                name: "pk_units",
                table: "units");

            migrationBuilder.DropPrimaryKey(
                name: "pk_refresh_tokens",
                table: "refresh_tokens");

            migrationBuilder.DropPrimaryKey(
                name: "pk_records",
                table: "records");

            migrationBuilder.DropPrimaryKey(
                name: "pk_measurement_sessions",
                table: "measurement_sessions");

            migrationBuilder.DropPrimaryKey(
                name: "pk_grade_criteria",
                table: "grade_criteria");

            migrationBuilder.DropPrimaryKey(
                name: "pk_categories",
                table: "categories");

            migrationBuilder.RenameColumn(
                name: "role",
                table: "users",
                newName: "Role");

            migrationBuilder.RenameColumn(
                name: "rank",
                table: "users",
                newName: "Rank");

            migrationBuilder.RenameColumn(
                name: "name",
                table: "users",
                newName: "Name");

            migrationBuilder.RenameColumn(
                name: "id",
                table: "users",
                newName: "Id");

            migrationBuilder.RenameColumn(
                name: "updated_at",
                table: "users",
                newName: "UpdatedAt");

            migrationBuilder.RenameColumn(
                name: "unit_id",
                table: "users",
                newName: "UnitId");

            migrationBuilder.RenameColumn(
                name: "password_hash",
                table: "users",
                newName: "PasswordHash");

            migrationBuilder.RenameColumn(
                name: "military_id",
                table: "users",
                newName: "MilitaryId");

            migrationBuilder.RenameColumn(
                name: "is_active",
                table: "users",
                newName: "IsActive");

            migrationBuilder.RenameColumn(
                name: "created_at",
                table: "users",
                newName: "CreatedAt");

            migrationBuilder.RenameIndex(
                name: "ix_users_unit_id",
                table: "users",
                newName: "IX_users_UnitId");

            migrationBuilder.RenameIndex(
                name: "ix_users_military_id",
                table: "users",
                newName: "IX_users_MilitaryId");

            migrationBuilder.RenameColumn(
                name: "name",
                table: "units",
                newName: "Name");

            migrationBuilder.RenameColumn(
                name: "id",
                table: "units",
                newName: "Id");

            migrationBuilder.RenameColumn(
                name: "parent_id",
                table: "units",
                newName: "ParentId");

            migrationBuilder.RenameColumn(
                name: "created_at",
                table: "units",
                newName: "CreatedAt");

            migrationBuilder.RenameIndex(
                name: "ix_units_parent_id",
                table: "units",
                newName: "IX_units_ParentId");

            migrationBuilder.RenameColumn(
                name: "token",
                table: "refresh_tokens",
                newName: "Token");

            migrationBuilder.RenameColumn(
                name: "id",
                table: "refresh_tokens",
                newName: "Id");

            migrationBuilder.RenameColumn(
                name: "user_id",
                table: "refresh_tokens",
                newName: "UserId");

            migrationBuilder.RenameColumn(
                name: "expires_at",
                table: "refresh_tokens",
                newName: "ExpiresAt");

            migrationBuilder.RenameColumn(
                name: "created_at",
                table: "refresh_tokens",
                newName: "CreatedAt");

            migrationBuilder.RenameIndex(
                name: "ix_refresh_tokens_user_id",
                table: "refresh_tokens",
                newName: "IX_refresh_tokens_UserId");

            migrationBuilder.RenameColumn(
                name: "value",
                table: "records",
                newName: "Value");

            migrationBuilder.RenameColumn(
                name: "note",
                table: "records",
                newName: "Note");

            migrationBuilder.RenameColumn(
                name: "grade",
                table: "records",
                newName: "Grade");

            migrationBuilder.RenameColumn(
                name: "id",
                table: "records",
                newName: "Id");

            migrationBuilder.RenameColumn(
                name: "user_id",
                table: "records",
                newName: "UserId");

            migrationBuilder.RenameColumn(
                name: "session_id",
                table: "records",
                newName: "SessionId");

            migrationBuilder.RenameColumn(
                name: "created_at",
                table: "records",
                newName: "CreatedAt");

            migrationBuilder.RenameColumn(
                name: "category_id",
                table: "records",
                newName: "CategoryId");

            migrationBuilder.RenameIndex(
                name: "ix_records_user_id",
                table: "records",
                newName: "IX_records_UserId");

            migrationBuilder.RenameIndex(
                name: "ix_records_session_id_user_id_category_id",
                table: "records",
                newName: "IX_records_SessionId_UserId_CategoryId");

            migrationBuilder.RenameIndex(
                name: "ix_records_category_id",
                table: "records",
                newName: "IX_records_CategoryId");

            migrationBuilder.RenameColumn(
                name: "note",
                table: "measurement_sessions",
                newName: "Note");

            migrationBuilder.RenameColumn(
                name: "location",
                table: "measurement_sessions",
                newName: "Location");

            migrationBuilder.RenameColumn(
                name: "id",
                table: "measurement_sessions",
                newName: "Id");

            migrationBuilder.RenameColumn(
                name: "unit_id",
                table: "measurement_sessions",
                newName: "UnitId");

            migrationBuilder.RenameColumn(
                name: "measured_at",
                table: "measurement_sessions",
                newName: "MeasuredAt");

            migrationBuilder.RenameColumn(
                name: "created_at",
                table: "measurement_sessions",
                newName: "CreatedAt");

            migrationBuilder.RenameColumn(
                name: "admin_id",
                table: "measurement_sessions",
                newName: "AdminId");

            migrationBuilder.RenameIndex(
                name: "ix_measurement_sessions_unit_id",
                table: "measurement_sessions",
                newName: "IX_measurement_sessions_UnitId");

            migrationBuilder.RenameIndex(
                name: "ix_measurement_sessions_admin_id",
                table: "measurement_sessions",
                newName: "IX_measurement_sessions_AdminId");

            migrationBuilder.RenameColumn(
                name: "grade",
                table: "grade_criteria",
                newName: "Grade");

            migrationBuilder.RenameColumn(
                name: "gender",
                table: "grade_criteria",
                newName: "Gender");

            migrationBuilder.RenameColumn(
                name: "id",
                table: "grade_criteria",
                newName: "Id");

            migrationBuilder.RenameColumn(
                name: "sort_order",
                table: "grade_criteria",
                newName: "SortOrder");

            migrationBuilder.RenameColumn(
                name: "rank_group",
                table: "grade_criteria",
                newName: "RankGroup");

            migrationBuilder.RenameColumn(
                name: "min_value",
                table: "grade_criteria",
                newName: "MinValue");

            migrationBuilder.RenameColumn(
                name: "max_value",
                table: "grade_criteria",
                newName: "MaxValue");

            migrationBuilder.RenameColumn(
                name: "category_id",
                table: "grade_criteria",
                newName: "CategoryId");

            migrationBuilder.RenameIndex(
                name: "ix_grade_criteria_category_id",
                table: "grade_criteria",
                newName: "IX_grade_criteria_CategoryId");

            migrationBuilder.RenameColumn(
                name: "unit",
                table: "categories",
                newName: "Unit");

            migrationBuilder.RenameColumn(
                name: "name",
                table: "categories",
                newName: "Name");

            migrationBuilder.RenameColumn(
                name: "description",
                table: "categories",
                newName: "Description");

            migrationBuilder.RenameColumn(
                name: "id",
                table: "categories",
                newName: "Id");

            migrationBuilder.RenameColumn(
                name: "is_active",
                table: "categories",
                newName: "IsActive");

            migrationBuilder.AddPrimaryKey(
                name: "PK_users",
                table: "users",
                column: "Id");

            migrationBuilder.AddPrimaryKey(
                name: "PK_units",
                table: "units",
                column: "Id");

            migrationBuilder.AddPrimaryKey(
                name: "PK_refresh_tokens",
                table: "refresh_tokens",
                column: "Id");

            migrationBuilder.AddPrimaryKey(
                name: "PK_records",
                table: "records",
                column: "Id");

            migrationBuilder.AddPrimaryKey(
                name: "PK_measurement_sessions",
                table: "measurement_sessions",
                column: "Id");

            migrationBuilder.AddPrimaryKey(
                name: "PK_grade_criteria",
                table: "grade_criteria",
                column: "Id");

            migrationBuilder.AddPrimaryKey(
                name: "PK_categories",
                table: "categories",
                column: "Id");

            migrationBuilder.AddForeignKey(
                name: "FK_grade_criteria_categories_CategoryId",
                table: "grade_criteria",
                column: "CategoryId",
                principalTable: "categories",
                principalColumn: "Id",
                onDelete: ReferentialAction.Cascade);

            migrationBuilder.AddForeignKey(
                name: "FK_measurement_sessions_units_UnitId",
                table: "measurement_sessions",
                column: "UnitId",
                principalTable: "units",
                principalColumn: "Id");

            migrationBuilder.AddForeignKey(
                name: "FK_measurement_sessions_users_AdminId",
                table: "measurement_sessions",
                column: "AdminId",
                principalTable: "users",
                principalColumn: "Id");

            migrationBuilder.AddForeignKey(
                name: "FK_records_categories_CategoryId",
                table: "records",
                column: "CategoryId",
                principalTable: "categories",
                principalColumn: "Id",
                onDelete: ReferentialAction.Cascade);

            migrationBuilder.AddForeignKey(
                name: "FK_records_measurement_sessions_SessionId",
                table: "records",
                column: "SessionId",
                principalTable: "measurement_sessions",
                principalColumn: "Id");

            migrationBuilder.AddForeignKey(
                name: "FK_records_users_UserId",
                table: "records",
                column: "UserId",
                principalTable: "users",
                principalColumn: "Id",
                onDelete: ReferentialAction.Cascade);

            migrationBuilder.AddForeignKey(
                name: "FK_refresh_tokens_users_UserId",
                table: "refresh_tokens",
                column: "UserId",
                principalTable: "users",
                principalColumn: "Id",
                onDelete: ReferentialAction.Cascade);

            migrationBuilder.AddForeignKey(
                name: "FK_units_units_ParentId",
                table: "units",
                column: "ParentId",
                principalTable: "units",
                principalColumn: "Id");

            migrationBuilder.AddForeignKey(
                name: "FK_users_units_UnitId",
                table: "users",
                column: "UnitId",
                principalTable: "units",
                principalColumn: "Id");
        }
    }
}
