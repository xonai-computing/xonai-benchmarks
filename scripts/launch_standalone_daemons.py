import time

import boto3
import sys
from subprocess import PIPE, Popen

master_dns = ''
master_url = ''
worker_dns = []

ec2 = boto3.resource('ec2', "us-east-1")  # ToDo: Change region
running_instances = ec2.instances.filter(Filters=[{'Name': 'instance-state-name', 'Values': ['running']}])
for instance in running_instances:
    if instance.tags is not None:
        for tags in instance.tags:
            if tags["Key"] == 'Purpose' and tags["Value"] == 'Master':
                master_dns = instance.public_dns_name
                master_url = instance.private_dns_name
            if tags["Key"] == 'Purpose' and tags["Value"] == 'Worker':
                worker_dns.append(instance.public_dns_name)

if master_dns == '' or master_url == '':
    print("No Cluster Master node running")
    sys.exit(1)

############################
# Starting Master daemon
command = '$SPARK_HOME/sbin/start-master.sh'
stream = Popen(['ssh', '-i', '~/.aws/YYY.pem', 'ec2-user@' + master_dns, command],  # ToDo: location of .pem file
               stdin=PIPE, stdout=PIPE)
response = stream.stdout.read().decode('utf-8')
print('-' * 100)
print('Starting Master daemon ' + master_dns + ':')
print(response)
print('-' * 100)
print('Configuring Spark conf:')
command = 'aws s3 cp s3://path/to/spark-defaults.conf $SPARK_HOME/conf'  # ToDo: S3 path to prewritten spark-defaults.conf file
stream = Popen(['ssh', '-i', '~/.aws/YYY.pem', 'ec2-user@' + master_dns, command],  # ToDo: location of .pem file
               stdin=PIPE, stdout=PIPE)
response = stream.stdout.read().decode('utf-8')
print('-' * 100)
print(response)
############################
# Starting Worker daemons
time.sleep(10)
print('-' * 100)
spark_master_url = 'spark://' + master_url + ':7077'
command = '$SPARK_HOME/sbin/start-worker.sh ' + spark_master_url
for worker in worker_dns:
    stream = Popen(['ssh', '-i', '~/.aws/YYY.pem', 'ec2-user@' + worker, command],  # ToDo: location of .pem file
                   stdin=PIPE, stdout=PIPE)
    response = stream.stdout.read().decode('utf-8')
    print('-' * 50)
    print('Starting Worker daemon ' + worker + ":")
    print(response)
    print('-' * 50)

print('-' * 100)
print('Finished setting up Spark Cluster')
