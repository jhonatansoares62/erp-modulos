; ============================================================
; ERP Kit - Modulos Plugaveis - Inno Setup Installer
; Gera instalador .exe para Windows
; ============================================================
;
; Pre-requisitos para compilar:
;   1. Inno Setup 6+ instalado (https://jrsoftware.org/isinfo.php)
;   2. Executar build.cmd antes (gera os JARs)
;
; Para compilar:
;   "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" erp-modulos-installer.iss
;
; Ou abrir este arquivo no Inno Setup Compiler e clicar em Build > Compile
; ============================================================

#define MyAppName "ERP Kit - Modulos"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "ERP Kit"
#define MyAppURL "http://localhost:9091/swagger-ui.html"
#define MyProjectDir ".."

[Setup]
AppId={{A1B2C3D4-E5F6-7890-ABCD-EF1234567890}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
DefaultDirName={autopf}\ERPKit
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
OutputDir={#MyProjectDir}\installer\output
OutputBaseFilename=ERPKit-Setup-{#MyAppVersion}
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
SetupIconFile=
UninstallDisplayIcon={app}\bin\deploy.cmd
DisableProgramGroupPage=yes
LicenseFile=
InfoBeforeFile=

[Languages]
Name: "brazilianportuguese"; MessagesFile: "compiler:Languages\BrazilianPortuguese.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Types]
Name: "full"; Description: "Instalacao completa (todos os modulos)"
Name: "email"; Description: "Apenas API de Email"
Name: "storage"; Description: "Apenas API de Storage"
Name: "custom"; Description: "Personalizada"; Flags: iscustom

[Components]
Name: "email"; Description: "API de Email (porta 9091)"; Types: full email custom
Name: "storage"; Description: "API de Storage (porta 8085)"; Types: full storage custom
Name: "scripts"; Description: "Scripts de gerenciamento"; Types: full email storage custom; Flags: fixed

[Files]
; JARs dos modulos
Source: "{#MyProjectDir}\api-email\target\api-email-*.jar"; DestDir: "{app}\lib"; Components: email; Excludes: "*.original"; Flags: ignoreversion
Source: "{#MyProjectDir}\api-storage\target\api-storage-*.jar"; DestDir: "{app}\lib"; Components: storage; Excludes: "*.original"; Flags: ignoreversion

; Scripts
Source: "{#MyProjectDir}\scripts\deploy.cmd"; DestDir: "{app}\bin"; Components: scripts; Flags: ignoreversion
Source: "{#MyProjectDir}\scripts\build.cmd"; DestDir: "{app}\bin"; Components: scripts; Flags: ignoreversion

[Dirs]
Name: "{app}\logs"; Permissions: users-modify
Name: "{app}\uploads"; Permissions: users-modify
Name: "{app}\config"; Permissions: users-modify

[Icons]
Name: "{group}\Iniciar ERP Kit"; Filename: "{cmd}"; Parameters: "/k ""{app}\bin\deploy.cmd"" start"; WorkingDir: "{app}"; Components: scripts
Name: "{group}\Parar ERP Kit"; Filename: "{cmd}"; Parameters: "/k ""{app}\bin\deploy.cmd"" stop"; WorkingDir: "{app}"; Components: scripts
Name: "{group}\Status ERP Kit"; Filename: "{cmd}"; Parameters: "/k ""{app}\bin\deploy.cmd"" status"; WorkingDir: "{app}"; Components: scripts
Name: "{group}\Swagger - API Email"; Filename: "http://localhost:9091/swagger-ui.html"; Components: email
Name: "{group}\Swagger - API Storage"; Filename: "http://localhost:8085/swagger-ui.html"; Components: storage
Name: "{group}\Pasta de Instalacao"; Filename: "{app}"
Name: "{group}\Desinstalar {#MyAppName}"; Filename: "{uninstallexe}"

Name: "{autodesktop}\ERP Kit - Iniciar"; Filename: "{cmd}"; Parameters: "/k ""{app}\bin\deploy.cmd"" start"; WorkingDir: "{app}"; Tasks: desktopicon
Name: "{autodesktop}\ERP Kit - Status"; Filename: "{cmd}"; Parameters: "/k ""{app}\bin\deploy.cmd"" status"; WorkingDir: "{app}"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Criar atalhos na area de trabalho"; GroupDescription: "Atalhos:"

[Run]
; Parar modulos que possam estar rodando antes de instalar
Filename: "{cmd}"; Parameters: "/c ""{app}\bin\deploy.cmd"" stop"; Flags: runhidden; StatusMsg: "Parando modulos existentes..."

[UninstallRun]
; Parar modulos antes de desinstalar
Filename: "{cmd}"; Parameters: "/c ""{app}\bin\deploy.cmd"" stop"; Flags: runhidden; RunOnceId: "StopServices"

[Code]
var
  JavaPage: TOutputMsgWizardPage;
  DbPage: TInputQueryWizardPage;
  JavaFound: Boolean;

function JavaInstalled(): Boolean;
var
  ResultCode: Integer;
begin
  Result := Exec('java', '-version', '', SW_HIDE, ewWaitUntilTerminated, ResultCode) and (ResultCode = 0);
end;

procedure InitializeWizard();
begin
  // Pagina de verificacao do Java
  JavaPage := CreateOutputMsgPage(wpWelcome,
    'Verificacao de Pre-requisitos',
    'O ERP Kit requer Java 21 ou superior.');

  JavaFound := JavaInstalled();

  if JavaFound then
    JavaPage.RichEditViewer.RTFText := '{\rtf1 Java encontrado no sistema. \par\par Requisitos atendidos!}'
  else
    JavaPage.RichEditViewer.RTFText := '{\rtf1 {\b Java NAO encontrado!} \par\par O ERP Kit requer Java 21 (JDK) para funcionar. \par\par Baixe em: https://adoptium.net/ \par\par Instale o Java primeiro, depois execute este instalador novamente. \par Ou continue se o Java ja esta instalado em outro local.}';

  // Pagina de configuracao do banco
  DbPage := CreateInputQueryPage(wpSelectDir,
    'Configuracao do Banco de Dados',
    'Configure a conexao com o PostgreSQL.',
    'Informe os dados de conexao. Deixe os valores padrao se nao tiver certeza.');

  DbPage.Add('Host do PostgreSQL:', False);
  DbPage.Add('Porta:', False);
  DbPage.Add('Usuario:', False);
  DbPage.Add('Senha:', False);

  DbPage.Values[0] := 'localhost';
  DbPage.Values[1] := '5432';
  DbPage.Values[2] := 'erp_calhas';
  DbPage.Values[3] := 'erp_calhas_dev';
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  ConfigFile: String;
begin
  if CurStep = ssPostInstall then
  begin
    // Criar arquivo de configuracao env.cmd
    ConfigFile := ExpandConstant('{app}\config\env.cmd');
    if not FileExists(ConfigFile) then
    begin
      SaveStringToFile(ConfigFile,
        '@echo off' + #13#10 +
        ':: ERP Kit - Configuracao de Ambiente' + #13#10 +
        ':: Edite este arquivo conforme necessario' + #13#10 +
        '' + #13#10 +
        ':: Chave de API (deixe vazio para desabilitar)' + #13#10 +
        'set "API_KEY="' + #13#10 +
        '' + #13#10 +
        ':: PostgreSQL' + #13#10 +
        'set "DB_HOST=' + DbPage.Values[0] + '"' + #13#10 +
        'set "DB_PORT=' + DbPage.Values[1] + '"' + #13#10 +
        'set "DB_USER=' + DbPage.Values[2] + '"' + #13#10 +
        'set "DB_PASS=' + DbPage.Values[3] + '"' + #13#10 +
        '' + #13#10 +
        ':: Storage' + #13#10 +
        'set "STORAGE_DIR=' + ExpandConstant('{app}') + '\uploads"' + #13#10 +
        'set "STORAGE_BASE_URL=http://localhost:8085"' + #13#10,
        False);
    end;
  end;
end;

function NextButtonClick(CurPageID: Integer): Boolean;
begin
  Result := True;

  // Aviso se Java nao encontrado
  if (CurPageID = JavaPage.ID) and (not JavaFound) then
  begin
    if MsgBox('Java nao foi encontrado. Deseja continuar mesmo assim?' + #13#10 +
              'O ERP Kit nao funcionara sem Java 21 instalado.',
              mbConfirmation, MB_YESNO) = IDNO then
      Result := False;
  end;
end;
