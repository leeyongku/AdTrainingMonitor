# Windows 서비스 자동 실행 배포 절차

서버(PC)가 재부팅되거나 사용자 로그인 없이도 API가 계속 떠 있도록, Windows 서비스로 등록해서
부팅 시 자동 시작되게 하는 절차입니다.

## 0. 사전 준비 (코드 변경 사항 - 이미 적용됨)

- [TrainingMonitor.csproj](../TrainingMonitor.csproj)에 `Microsoft.Extensions.Hosting.WindowsServices` 패키지 추가됨.
- [Program.cs](../Program.cs)에 `builder.Host.UseWindowsService();` 추가됨.
  - `dotnet run`으로 실행할 때는 아무 영향 없음.
  - Windows 서비스로 등록되어 SCM(서비스 제어 관리자)이 구동할 때만 서비스 생명주기(시작/중지 신호 등)에 맞게 동작.

## 1. 운영용 설정 값 준비

`appsettings.json`의 `ConnectionStrings:Default`, `Jwt:Secret`은 개발용 플레이스홀더입니다.
Windows 서비스는 로그인한 사용자 계정의 User Secrets(`dotnet user-secrets`)를 읽지 못하므로,
아래 둘 중 하나의 방법으로 실제 운영 값을 넣어야 합니다.

**방법 A - appsettings.Production.json 파일 생성** (git에는 올리지 말 것)

```json
{
  "ConnectionStrings": {
    "Default": "Host=...;Port=...;Database=training_monitor;Username=...;Password=실제값"
  },
  "Jwt": {
    "Secret": "실제-운영용-32자-이상-키"
  },
  "Urls": "http://0.0.0.0:5133"
}
```

**방법 B - 시스템 환경 변수로 설정** (관리자 PowerShell)

```powershell
setx ConnectionStrings__Default "Host=...;Password=실제값" /M
setx Jwt__Secret "실제-운영용-32자-이상-키" /M
setx ASPNETCORE_URLS "http://0.0.0.0:5133" /M
```

> `ASPNETCORE_URLS`(또는 `Urls`)를 지정하지 않으면 기본값인 `http://localhost:5000`으로만 열려
> 외부 PC에서 접속이 안 될 수 있습니다.

## 2. 게시(publish)

```powershell
dotnet publish -c Release -o C:\services\TrainingMonitor
```

## 3. DB 마이그레이션 적용

[Program.cs](../Program.cs)는 `Development` 환경에서만 서버 시작 시 자동으로 마이그레이션을 적용합니다.
서비스로 등록하면 기본 환경은 `Production`이 되어 자동 적용되지 않으므로, 배포 전 수동으로 한 번
적용해야 합니다.

```powershell
dotnet ef database update
```

## 4. 서비스 등록 + 자동 시작 설정 (관리자 PowerShell)

```powershell
sc.exe create TrainingMonitorApi binPath= "C:\services\TrainingMonitor\TrainingMonitor.exe" start= auto
sc.exe description TrainingMonitorApi "훈련 모니터링 API 서버"
sc.exe start TrainingMonitorApi
```

> `binPath=` 뒤에 공백이 반드시 있어야 합니다 (`sc.exe` 문법 특성).

## 5. 확인

- `services.msc`를 열어 "TrainingMonitorApi" 서비스의 상태가 **실행 중**, 시작 유형이 **자동**인지 확인.
- 브라우저 또는 curl로 API 응답 확인:
  ```powershell
  curl http://localhost:5133/api/auth/login -Method POST -Body '{"militaryId":"군번","password":"비밀번호"}' -ContentType "application/json"
  ```
- PC를 재부팅한 뒤에도 로그인 없이 서비스가 자동으로 떠 있는지 확인.

## 6. 재배포(업데이트) 시 절차

```powershell
sc.exe stop TrainingMonitorApi
dotnet publish -c Release -o C:\services\TrainingMonitor
dotnet ef database update   # 마이그레이션이 추가된 경우에만
sc.exe start TrainingMonitorApi
```

## 참고 - 현재 로컬 개발 환경과의 차이

| 항목 | 로컬 개발 (`dotnet run` / VS Code 디버그) | Windows 서비스 배포 |
|---|---|---|
| 환경(`ASPNETCORE_ENVIRONMENT`) | Development | Production (기본값) |
| Jwt/DB 설정 값 출처 | User Secrets | `appsettings.Production.json` 또는 환경 변수 |
| DB 마이그레이션 | 서버 시작 시 자동 적용 | `dotnet ef database update`로 수동 적용 |
| Swagger UI | 활성화 (`/swagger`) | 비활성화 |
