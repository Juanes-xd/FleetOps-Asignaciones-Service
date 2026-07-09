#!/bin/bash
set -e

REGION="us-east-1"
aws configure set aws_access_key_id test
aws configure set aws_secret_access_key test
TOPIC_NAME="queue_assignations"
QUEUE_NAME="queue_assignations"
DLQ_NAME="queue_assignations-dlq"
ENDPOINT_URL="http://localhost:4566"

echo "🚀 Inicializando recursos en LocalStack..."

# Crear DLQ
DLQ_URL=$(aws --endpoint-url=$ENDPOINT_URL sqs create-queue --queue-name "$DLQ_NAME" --region "$REGION" --query QueueUrl --output text)
DLQ_ARN=$(aws --endpoint-url=$ENDPOINT_URL sqs get-queue-attributes --queue-url "$DLQ_URL" --attribute-names QueueArn --region "$REGION" --query Attributes.QueueArn --output text)
echo "✅ DLQ creada: $DLQ_URL"

# Crear cola principal con DLQ
QUEUE_URL=$(aws --endpoint-url=$ENDPOINT_URL sqs create-queue --queue-name "$QUEUE_NAME" --region "$REGION" \
  --attributes "{\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"$DLQ_ARN\\\",\\\"maxReceiveCount\\\":\\\"5\\\"}\"}" \
  --query QueueUrl --output text)
QUEUE_ARN=$(aws --endpoint-url=$ENDPOINT_URL sqs get-queue-attributes --queue-url "$QUEUE_URL" --attribute-names QueueArn --region "$REGION" --query Attributes.QueueArn --output text)
echo "✅ Cola creada: $QUEUE_URL"

# Crear topic SNS
TOPIC_ARN=$(aws --endpoint-url=$ENDPOINT_URL sns create-topic --name "$TOPIC_NAME" --region "$REGION" --query TopicArn --output text)
echo "✅ Topic creado: $TOPIC_ARN"

# Configurar política de la cola
aws --endpoint-url=$ENDPOINT_URL sqs set-queue-attributes --queue-url "$QUEUE_URL" --region "$REGION" --attributes "{
  \"Policy\": \"{\\\"Version\\\":\\\"2012-10-17\\\",\\\"Statement\\\":[{\\\"Effect\\\":\\\"Allow\\\",\\\"Principal\\\":\\\"*\\\",\\\"Action\\\":\\\"sqs:SendMessage\\\",\\\"Resource\\\":\\\"$QUEUE_ARN\\\",\\\"Condition\\\":{\\\"ArnEquals\\\":{\\\"aws:SourceArn\\\":\\\"$TOPIC_ARN\\\"}}}]}\"
}"
echo "✅ Política de cola configurada"

# Suscribir SQS a SNS
aws --endpoint-url=$ENDPOINT_URL sns subscribe --topic-arn "$TOPIC_ARN" --protocol sqs --notification-endpoint "$QUEUE_ARN" --region "$REGION"
echo "✅ Suscripción SNS -> SQS creada"

echo ""
echo "📋 Resumen de recursos:"
echo "   Topic ARN: $TOPIC_ARN"
echo "   Queue URL: $QUEUE_URL"
echo "   DLQ URL:   $DLQ_URL"
echo ""
echo "✅ LocalStack inicializado correctamente!"