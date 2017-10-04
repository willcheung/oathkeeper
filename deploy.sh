#!/bin/bash
gcloud compute --project "contextsmith" copy-files --zone "us-west1-b" target/oathkeeper-*[^s].jar contextsmith-backend-1:.
