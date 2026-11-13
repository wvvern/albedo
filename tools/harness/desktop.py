"""Starts a process on a hidden Windows desktop, where its windows do not show, take focus or show in the taskbar."""

import ctypes
import os
from ctypes import wintypes
from dataclasses import dataclass
from functools import cache
from pathlib import Path

DESKTOP_NAME = "AlbedoHarness"
GENERIC_ALL = 0x10000000
CREATE_NO_WINDOW = 0x08000000
CREATE_UNICODE_ENVIRONMENT = 0x00000400


class STARTUPINFO(ctypes.Structure):
    _fields_ = (
        ("cb", wintypes.DWORD),
        ("lpReserved", wintypes.LPWSTR),
        ("lpDesktop", wintypes.LPWSTR),
        ("lpTitle", wintypes.LPWSTR),
        ("dwX", wintypes.DWORD),
        ("dwY", wintypes.DWORD),
        ("dwXSize", wintypes.DWORD),
        ("dwYSize", wintypes.DWORD),
        ("dwXCountChars", wintypes.DWORD),
        ("dwYCountChars", wintypes.DWORD),
        ("dwFillAttribute", wintypes.DWORD),
        ("dwFlags", wintypes.DWORD),
        ("wShowWindow", wintypes.WORD),
        ("cbReserved2", wintypes.WORD),
        ("lpReserved2", ctypes.c_void_p),
        ("hStdInput", wintypes.HANDLE),
        ("hStdOutput", wintypes.HANDLE),
        ("hStdError", wintypes.HANDLE),
    )


class PROCESS_INFORMATION(ctypes.Structure):
    _fields_ = (
        ("hProcess", wintypes.HANDLE),
        ("hThread", wintypes.HANDLE),
        ("dwProcessId", wintypes.DWORD),
        ("dwThreadId", wintypes.DWORD),
    )


@dataclass(frozen=True)
class Launch:
    """A command line to run in a folder, with its output in a log file."""

    command_line: str
    cwd: Path
    log: Path
    # Variables for the process on top of the ones of this process.
    environment: tuple[tuple[str, str], ...] = ()


@cache
def hidden_desktop() -> int:
    """The desktop of the harness games, which Windows makes at the first call and gives again at the next ones.

    The handle stays open as long as this process, because the desktop goes away with its last handle, and a process that has just
    started does not hold one yet.
    """
    user32 = ctypes.WinDLL("user32", use_last_error=True)
    user32.CreateDesktopW.restype = wintypes.HANDLE
    user32.CreateDesktopW.argtypes = (wintypes.LPCWSTR, wintypes.LPCWSTR, ctypes.c_void_p, wintypes.DWORD, wintypes.DWORD, ctypes.c_void_p)
    handle = user32.CreateDesktopW(DESKTOP_NAME, None, None, 0, GENERIC_ALL, None)
    if not handle:
        raise ctypes.WinError(ctypes.get_last_error())
    return handle


def environment_block(additions: tuple[tuple[str, str], ...]) -> ctypes.Array | None:
    if not additions:
        return None
    variables = dict(os.environ) | dict(additions)
    return ctypes.create_unicode_buffer("".join(f"{name}={value}\0" for name, value in variables.items()) + "\0")


def start_hidden(launch: Launch) -> int:
    """Runs the command line through cmd.exe on the hidden desktop and returns the process id of that cmd.exe."""
    hidden_desktop()
    kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel32.CreateProcessW.argtypes = (
        wintypes.LPCWSTR,
        wintypes.LPWSTR,
        ctypes.c_void_p,
        ctypes.c_void_p,
        wintypes.BOOL,
        wintypes.DWORD,
        ctypes.c_void_p,
        wintypes.LPCWSTR,
        ctypes.POINTER(STARTUPINFO),
        ctypes.POINTER(PROCESS_INFORMATION),
    )
    startup = STARTUPINFO()
    startup.cb = ctypes.sizeof(startup)
    startup.lpDesktop = DESKTOP_NAME
    process = PROCESS_INFORMATION()
    command = ctypes.create_unicode_buffer(f'cmd.exe /s /c "{launch.command_line} > "{launch.log}" 2>&1"')
    environment = environment_block(launch.environment)
    # CREATE_NO_WINDOW: no console window on the hidden desktop.
    flags = CREATE_NO_WINDOW | (CREATE_UNICODE_ENVIRONMENT if environment else 0)
    if not kernel32.CreateProcessW(
        None, command, None, None, False, flags, environment, str(launch.cwd), ctypes.byref(startup), ctypes.byref(process)
    ):
        raise ctypes.WinError(ctypes.get_last_error())
    kernel32.CloseHandle(wintypes.HANDLE(process.hThread))
    kernel32.CloseHandle(wintypes.HANDLE(process.hProcess))
    return process.dwProcessId
