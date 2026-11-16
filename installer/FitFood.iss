#define AppName "FitFood"
#define AppVersion "0.1.0"

[Setup]
AppId={{BC7B28E4-546A-48B2-A0F2-913677C5A487}
AppName={#AppName}
AppVersion={#AppVersion}
DefaultDirName={localappdata}\Programs\FitFood
DefaultGroupName={#AppName}
OutputDir=..\dist\installer
OutputBaseFilename=FitFood-Setup-{#AppVersion}
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=lowest
Compression=lzma
SolidCompression=yes
WizardStyle=modern

[Files]
Source: "..\dist\FitFood.exe"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\FitFood"; Filename: "{app}\FitFood.exe"
Name: "{autodesktop}\FitFood"; Filename: "{app}\FitFood.exe"

[Run]
Filename: "{app}\FitFood.exe"; Description: "Abrir FitFood"; Flags: postinstall nowait skipifsilent
