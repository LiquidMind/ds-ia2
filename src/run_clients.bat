:@echo off
set /a x=23000
:while
if %x% lss 23010 (
  echo %x%

  start "Slave at port %x%" java -cp ".;../lib/commons-cli-1.3.jar" Node -s -b --ip:port "127.0.0.1:%x%" -th "1000" -l "../log/log_slave_%x%.txt"

  set /a x+=1
  goto :while
)
:echo Test :D
