# Sample project to reproduce a native memory leak for https://bugs.openjdk.org/browse/JDK-8335121

This repository present a complete sample to reproduce a native memory leak when using JFR with thread pinning events and the snowflake jdbc driver.

## High level overview
This application is a standard Spring Boot application with 4 main components : 
- A dummy class implemented in `SnowflakeDummyJob` that insert data into snowflake
- A JFR event listener listening on thread pinned events implemented in `JfrEventLifecycle`
- A component to print pinned thread details to a temp file implemented in `JfrVirtualThreadPinnedEventHandler`
- A native memory tracker implemented in `NativeMemoryTracker` to easily and in a self-contained way track native memory

### Native memory tracking
Since this is a relatively slow leak, I wanted to automate the native memory tracking over time.

Therefore, I added a component that use the `jcmd` `baseline` and `detail.diff` facilities to show the progression of the `Tracing` memory category.

The `NativeMemoryTracker` takes the baseline on boot and then each 5 minutes will run the `jcmd PID -VM.native_memory -detail.diff` command and output the result into a file prefixed with `jfr-leak-diff-`, postfixed with the timestamp and outputed in a temp directory.

The temporary directory can be found in the application logs, on boot, in an entry that will look like this : 
```
c.m.jfr.pinning.NativeMemoryTracker : Created temp directory: /var/folders/jj/8969v2v92r764vnrmvhr679m0000gn/T/jfr-leak-2024-07-03T11:13:26-15165497133962784559
```

### How to run
For this to work, you'll need access to a snowflake database. With the proper credentials (you can request them at `jebeaudet at gmail.com`) set up : 
- `mvn clean package`
- `_JAVA_OPTIONS='--add-opens=java.base/java.nio=ALL-UNNAMED -Dsnowflake.password=INSERT_PASSWORD_HERE -XX:MaxDirectMemorySize=128M -Xms100M -Xmx5000M -XX:+AlwaysPreTouch -XX:NativeMemoryTracking=detail -Xlog:gc*:gc.log::filecount=0 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/dump/oom.hprof' java -jar target/jfr-native-memory-leak-0.0.1-SNAPSHOT.jar`

### Leak details
Using the files that are outputted in the temporary folder, it is possible to see a slow native memory leak within the category `Tracing`. This leak present itself even though there are _no_ thread pinned event. Here is an output of a run : 
```
grep -r "Tracing (" .| sort -n
./jfr-leak-diff-2024-07-03T09:56:47:- Tracing (reserved=15685KB +92KB, committed=15685KB +92KB)
./jfr-leak-diff-2024-07-03T10:01:47:- Tracing (reserved=15769KB +176KB, committed=15769KB +176KB)
./jfr-leak-diff-2024-07-03T10:06:47:- Tracing (reserved=15854KB +261KB, committed=15854KB +261KB)
./jfr-leak-diff-2024-07-03T10:11:47:- Tracing (reserved=15940KB +347KB, committed=15940KB +347KB)
./jfr-leak-diff-2024-07-03T10:16:47:- Tracing (reserved=16025KB +432KB, committed=16025KB +432KB)
./jfr-leak-diff-2024-07-03T10:21:47:- Tracing (reserved=16110KB +517KB, committed=16110KB +517KB)
./jfr-leak-diff-2024-07-03T10:26:47:- Tracing (reserved=16194KB +601KB, committed=16194KB +601KB)
./jfr-leak-diff-2024-07-03T10:31:47:- Tracing (reserved=16279KB +685KB, committed=16279KB +685KB)
./jfr-leak-diff-2024-07-03T10:36:47:- Tracing (reserved=16361KB +768KB, committed=16361KB +768KB)
./jfr-leak-diff-2024-07-03T10:41:47:- Tracing (reserved=16445KB +852KB, committed=16445KB +852KB)
./jfr-leak-diff-2024-07-03T10:46:47:- Tracing (reserved=16531KB +938KB, committed=16531KB +938KB)
./jfr-leak-diff-2024-07-03T10:51:47:- Tracing (reserved=16615KB +1022KB, committed=16615KB +1022KB)
./jfr-leak-diff-2024-07-03T10:56:47:- Tracing (reserved=16701KB +1108KB, committed=16701KB +1108KB)
./jfr-leak-diff-2024-07-03T11:01:47:- Tracing (reserved=16784KB +1191KB, committed=16784KB +1191KB)
./jfr-leak-diff-2024-07-03T11:06:47:- Tracing (reserved=16867KB +1274KB, committed=16867KB +1274KB)
```

The triggering condition is the use of the library `snowflake-jdbc` which uses Apache Arrow under the hood. This library uses `ByteBuffer` [around here](https://github.com/apache/arrow/blob/main/java/memory/memory-core/src/main/java/org/apache/arrow/memory/util/MemoryUtil.java#L55) in a "non safe way" since it requires a `add-opens`.
