# Configuration Management through Ansible 

This directory provides the necessary configuration management steps that set up a single server for OSS Tracker deployment via `docker-compose`.

## Requirements

1. Setup Ansible on your machine: https://docs.ansible.com/ansible/latest/intro_installation.html 
1. Install python requirements (consider using bundled `extensions/setup/setup.sh`)
2. Update roles (consider using `extensions/setup/role_update.sh`)


## Run Ansible

Run the osstracker playbook, like so: 

```bash
cd ansible/plays
ansible-playbook -i ../production.ini osstracker.yml
```

The playbook installs docker, docker-compose and copies over the `docker-compose.yml` and `osstracker-ddl` directories. 

Once that's done, all you have to do is ssh into the new host and run: 

```bash
sudo su -
cd /opt/osstracker
export github_oauth= # your github api key - you'll need this for overcoming rate limiting.
export github_org= # the github organization whose repositories you want to track
docker-compose up --no-deps -d 
```

That's it!! Please _DO_ wait a few minutes though to give the scrapers some time to insert the first data.  

## Kibana Setup

1. After a few mins, go to the index mapping and select `osstracker`. Then use the `asOfYYYYMMDD` field for time. 
1. Restore visualizations: Upload the `visualizations.json` and `dashboard.json` files you'll find in the `kibana-dashboards` directory of this project.

Please note the 2nd dashboard might take a day till it shows the first data and a couple of days to make more sense. This is because there is a single entry per day for each project.

Enjoy!!! 


