# wget https://github.com/quhxuxm/ppaass/raw/master/distribute/target/ppaass-1.0-SNAPSHOT.zip
rm -rf /home/build/agent
rm -rf /home/build/proxy
rm /home/build/ppaass-1.0-SNAPSHOT.zip
rm -rf /home/sourcecode
# Build
mkdir /home/sourcecode
cd /home/sourcecode
git clone https://github.com/quhxuxm/ppaass-kt.git ppaass
cd /home/sourcecode/ppaass
git pull
mvn clean package
cp /home/sourcecode/ppaass/distribute/target/ppaass-1.0-SNAPSHOT.zip /home/build
cd /home/build
chmod 777 ppaass-1.0-SNAPSHOT.zip
unzip ppaass-1.0-SNAPSHOT.zip
chmod 777 /home/build/proxy/proxy-1.0-SNAPSHOT.jar
chmod 777 /home/build/proxy/startProxy.sh
cd /home/build/proxy
./startProxy.sh
ps -ef|grep java|grep proxy-1.0