start javaw -server -XX:+UseG1GC -XX:+UseCompressedOops -XX:+UseCompressedClassPointers -XX:+SegmentedCodeCache  -XX:+ExplicitGCInvokesConcurrent -jar agent-1.0-SNAPSHOT.jar --spring.config.location=classpath:application.properties,file:./application.properties > run.log 2>&1 &
