@rem ============================================================================
@rem MAGIC MADNESS — ROOT GRADLE WRAPPER FORWARDER
@rem ============================================================================
@rem #region 1. FORWARD TO MAGIC MADNESS GRADLEW
@echo off
pushd "%~dp0Magic Madness"
call "%~dp0Magic Madness\gradlew.bat" %*
set EXIT_CODE=%ERRORLEVEL%
popd
exit /b %EXIT_CODE%
@rem #endregion
