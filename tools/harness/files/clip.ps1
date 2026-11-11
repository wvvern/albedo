# Prints the cursor clip rectangle and the virtual screen as "left top right bottom virtualLeft virtualTop virtualWidth virtualHeight". Read only.
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class ClipProbe {
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern bool GetClipCursor(out RECT rect);
    [DllImport("user32.dll")] public static extern int GetSystemMetrics(int index);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
}
"@
[void][ClipProbe]::SetProcessDPIAware()
$rect = New-Object ClipProbe+RECT
if (-not [ClipProbe]::GetClipCursor([ref]$rect)) { Write-Output "unreadable"; exit 0 }
"{0} {1} {2} {3} {4} {5} {6} {7}" -f $rect.Left, $rect.Top, $rect.Right, $rect.Bottom, [ClipProbe]::GetSystemMetrics(76), [ClipProbe]::GetSystemMetrics(77),
    [ClipProbe]::GetSystemMetrics(78), [ClipProbe]::GetSystemMetrics(79)
