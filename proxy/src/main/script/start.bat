java -jar -Xms256m -Xmx256m -XX:MaxDirectMemorySize=256m proxy-1.0-SNAPSHOT.jar --spring.config.location=classpath:application.properties,file:./application.properties > run.log 2>&1 &