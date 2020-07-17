# wget https://github.com/quhxuxm/ppaass/raw/master/distribute/target/ppaass-1.0-SNAPSHOT.zip
ps -ef | grep java | grep -v grep | awk '{print $2}' | xargs kill -9
rm -rf /home/build
rm -rf /home/sourcecode
# Build
mkdir /home/sourcecode
mkdir /home/build
cd /home/sourcecode
git clone https://github.com/quhxuxm/ppaass-kt.git ppaass
cd /home/sourcecode/ppaass
git pull
gradle
ps -ef | grep gradle | grep -v grep | awk '{print $2}' | xargs kill -9
cp /home/sourcecode/ppaass/proxy/build/distributions/proxy-boot.zip /home/build
cd /home/build
chmod 777 proxy-boot.zip
unzip proxy-boot.zip
cd /home/build/proxy-boot/bin
nohup ./proxy >run.log 2>&1 &
ps -ef|grep java|grep proxy
~