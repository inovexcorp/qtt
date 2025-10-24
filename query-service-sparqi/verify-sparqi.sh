#!/bin/bash
# SPARQi Verification Script
# Run this after starting Karaf to verify SPARQi is working

echo "=== SPARQi Verification ==="
echo ""

# Check if we're in the assembly directory
if [ ! -f "bin/karaf" ]; then
    echo "ERROR: Not in Karaf assembly directory"
    echo "Please cd to: query-service-distribution/target/assembly"
    exit 1
fi

echo "1. Checking if Karaf is running..."
if ! pgrep -f "karaf" > /dev/null; then
    echo "   ⚠️  Karaf not running. Start it with: bin/karaf"
    exit 1
else
    echo "   ✓ Karaf is running"
fi

echo ""
echo "2. Testing SPARQi health endpoint..."
HEALTH_RESPONSE=$(curl -k -s http://localhost:8080/queryrest/api/sparqi/health 2>/dev/null)
if [ -z "$HEALTH_RESPONSE" ]; then
    echo "   ⚠️  No response from health endpoint"
    echo "   This could mean:"
    echo "     - SPARQi bundle not started"
    echo "     - JAX-RS whiteboard not ready"
    echo "     - Web server not fully initialized"
else
    echo "   Response: $HEALTH_RESPONSE"
    if echo "$HEALTH_RESPONSE" | grep -q "status"; then
        echo "   ✓ SPARQi health endpoint is responding"
    fi
fi

echo ""
echo "3. To check bundle status in Karaf, run:"
echo "   bin/client -u karaf -p karaf 'bundle:list | grep sparqi'"
echo ""
echo "4. To check service status in Karaf, run:"
echo "   bin/client -u karaf -p karaf 'service:list | grep SparqiService'"
echo ""
echo "5. To view SPARQi logs:"
echo "   tail -f data/log/karaf.log | grep -i sparqi"
echo ""
echo "=== Configuration ==="
echo "SPARQi config file: etc/com.inovexcorp.queryservice.sparqi.cfg"
echo ""
echo "Required environment variables (if not set, service will be disabled):"
echo "  SPARQI_LLM_BASE_URL  - LLM endpoint (e.g., http://localhost:4000)"
echo "  SPARQI_LLM_API_KEY   - API key for LLM provider"
echo "  SPARQI_LLM_MODEL     - Model name (default: openai/gpt-4o)"
echo ""
echo "To set environment variables and restart:"
echo "  export SPARQI_LLM_BASE_URL=http://localhost:4000"
echo "  export SPARQI_LLM_API_KEY=your-key"
echo "  export SPARQI_LLM_MODEL=gpt-4o-mini"
echo "  bin/stop"
echo "  bin/karaf"
