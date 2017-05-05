#!/bin/bash
gcloud compute --project "contextsmith" copy-files --zone "us-west1-b" target/contextsmith-*[^s].jar contextsmith-backend-1:.
