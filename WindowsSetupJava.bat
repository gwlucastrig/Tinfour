@echo off
REM  
REM     PATH setup for Windows
REM  
REM     Configure this file for you own system using the following steps
REM         1.  If you have not already installed the Java Development Kit (JDK)
REM             or Java Runtime Environment (JRE) on your system, download and
REM             install your preferred version.  Unless you have special requirements,
REM             I recommend taking a recent version.  Tinfour requires
REM             Java version 8 or later.
REM         2.  Locate the Java installation location (also called the "installation path")
REM         3.  Modify the Windows commands shown below for JAVA_HOME and PATH to specify
REM             the correct settings for your computer's installation. Note
REM             that the setting for PATH below uses the "bin" folder,
REM             but that the JAVA_HOME does not include the "bin".
REM  
REM      If you wish, you may also set up this file to change your working
REM      directory to your folder by including the Windows "change directory" command
REM      Remove the comment introducer from the following command and modify it as appropriate.
REM           cd c:\Users\%USERNAME%\Documents
REM  
REM      If you wish to compile Tinfour and related software, you may also include
REM      the specifications for Maven in your PATH.   To do so, locate the Maven
REM      installation path and include the path to the Mava "bin" folder in the "set PATH" line.
REM      Note that compiling Java code requires the use of the JDK.
REM  
REM      On many Windows systems, a version of Java may already be installed.
REM      If so these settings may be unnecessary



set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_91
set PATH=C:\Program Files\Java\jdk1.8.0_91\bin;%PATH%
java -version
