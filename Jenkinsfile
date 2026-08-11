// Jenkins checks this repo out into its own workspace on every run, separate from the manual
// ~/CareerBridge_2.0 checkout on the EC2 host. .env is gitignored (by design -- it holds real
// secrets), so it never arrives via git; this pipeline restores it from a master copy that lives
// outside any git-managed directory, then refreshes VITE_API_BASE_URL to whatever public IP this
// EC2 instance currently has (it changes on every stop/start unless an Elastic IP is attached).
pipeline {
    agent any

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Restore secrets') {
            steps {
                // One-time setup: sudo cp ~/CareerBridge_2.0/.env /var/lib/jenkins/careerbridge.env
                // then sudo chown jenkins:jenkins /var/lib/jenkins/careerbridge.env
                sh 'cp /var/lib/jenkins/careerbridge.env .env'
            }
        }

        stage('Refresh public IP') {
            steps {
                // VITE_API_BASE_URL is baked into the frontend bundle at build time, not read at
                // container startup -- see the comment in docker-compose.yml on the frontend
                // service's build.args for why this has to be the browser's address, not a
                // container-network hostname.
                sh '''
                    TOKEN=$(curl -s -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")
                    NEW_IP=$(curl -s -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/public-ipv4)
                    sed -i "s|^VITE_API_BASE_URL=.*|VITE_API_BASE_URL=http://${NEW_IP}:8080/api|" .env
                    echo "Deploying for public IP: ${NEW_IP}"
                '''
            }
        }

        stage('Validate compose file') {
            steps {
                // Cheap sanity check before touching anything live -- fails fast on a syntax error
                // in docker-compose.yml instead of tearing down healthy containers first.
                sh 'docker-compose config -q'
            }
        }

        stage('Build') {
            steps {
                // COMPOSE_PARALLEL_LIMIT caps how many of the 13 services build at once. Left at
                // its default (unbounded), all 13 JDK/Maven processes compete for this box's CPU
                // simultaneously, on top of Jenkins' own JVM already running -- enough contention
                // that Jenkins' own "is the build still alive" heartbeat starves and the build gets
                // marked failed after ~14 minutes even though it's still genuinely progressing
                // (JENKINS-48300). 2 at a time trades wall-clock time for a build that actually
                // finishes instead of timing out.
                sh 'COMPOSE_PARALLEL_LIMIT=2 docker-compose build'
            }
        }

        stage('Deploy') {
            steps {
                sh 'docker-compose up -d'
            }
        }

        stage('Cleanup old images') {
            steps {
                // Every build leaves the previous image generation dangling (untagged) once
                // replaced. Left unchecked this fills the disk that was already tight once this
                // session (see ai_incident_log.md, 2026-08-11).
                sh 'docker image prune -f'
            }
        }
    }

    post {
        success {
            echo 'Deployed successfully.'
        }
        failure {
            echo 'Build or deploy failed -- check the stage that failed above. Containers from the previous successful deploy are still running; docker-compose up only replaces a service once its new image finishes building.'
        }
    }
}
