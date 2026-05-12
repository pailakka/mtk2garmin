import argparse
import base64
import os
import requests
import sys
import time
import subprocess
import shutil

# --- CONFIGURATION ---
API_KEY = os.environ.get("MML_API_KEY")

# Official MML OGC API Processes URL
BASE_URL = "https://avoin-paikkatieto.maanmittauslaitos.fi/tiedostopalvelu/ogcproc/v1"
PROCESS_ID = "kiinteistorekisterikartta_vektori_koko_suomi"

def parse_args():
    parser = argparse.ArgumentParser(description="MML OGC API Downloader")
    parser.add_argument(
        "process_id", 
        help="PROCESS_ID to be used (default: kiinteistorekisterikartta_vektori_koko_suomi)",
    )
    parser.add_argument(
        "output_path", 
        help="The full local path where the file should be saved (e.g., ./data/result.gpkg)"
    )
    return parser.parse_args()

def get_auth_headers():
    if not API_KEY or API_KEY == "YOUR_API_KEY_HERE":
        print("Error: Please set MML_API_KEY in the environment.")
        sys.exit(1)
        
    # User ID is API Key, Password is empty
    credentials = f"{API_KEY}:"
    encoded = base64.b64encode(credentials.encode('utf-8')).decode('utf-8')
    return {
        'Authorization': f'Basic {encoded}',
        'Content-Type': 'application/json'
    }

def main():
    args = parse_args()
    headers = get_auth_headers()
    
    # 1. Check if curl is available
    if not shutil.which("curl"):
        print("Error: 'curl' is not installed or not in your PATH.")
        sys.exit(1)

    PROCESS_ID = args.process_id
    print(f"--- Starting Process: {PROCESS_ID} ---")

    # 2. EXECUTE PROCESS
    execute_url = f"{BASE_URL}/processes/{PROCESS_ID}/execution"
    payload = {
        "id": PROCESS_ID,
        "inputs": {
            "fileFormatInput": "GPKG"
        }
    }

    try:
        resp = requests.post(execute_url, headers=headers, json=payload)
        resp.raise_for_status()
        job_id = resp.json().get('jobID')
    except Exception as e:
        print(f"Execution failed: {e}")
        if 'resp' in locals(): print(resp.text)
        sys.exit(1)

    print(f"Job started. ID: {job_id}")
    print("Waiting for completion (this may take time)...")

    # 3. POLL STATUS
    status_url = f"{BASE_URL}/jobs/{job_id}"
    
    while True:
        try:
            r = requests.get(status_url, headers=headers)
            r.raise_for_status()
            status_data = r.json()
            status = status_data.get('status')
            
            if status == 'successful':
                print("\nJob finished successfully!")
                break
            elif status in ['failed', 'dismissed']:
                print(f"\nJob failed: {status_data.get('message')}")
                sys.exit(1)
            
            # Simple progress indicator
            print(f"Status: {status}...", end='\r')
            time.sleep(5)
            
        except Exception as e:
            print(f"\nPolling error: {e}")
            time.sleep(5)

    # 4. GET DOWNLOAD URL
    results_url = f"{BASE_URL}/jobs/{job_id}/results/"
    r_res = requests.get(results_url, headers=headers)
    results_data = r_res.json()

    download_url = None
    # Parse MML results structure
    if 'results' in results_data and isinstance(results_data['results'], list):
        for item in results_data['results']:
            # Prioritize 'path' (direct file) over 'zipPath'
            if 'path' in item:
                download_url = item['path']
                break
            elif 'zipPath' in item:
                download_url = item['zipPath']
    
    if not download_url:
        print("Error: Could not find a valid download URL in results.")
        print(results_data)
        sys.exit(1)

    # 5. DOWNLOAD WITH CURL
    print(f"Downloading {download_url} with curl to: {args.output_path}")
    
    # We must pass the Authorization header to curl as well
    auth_header_val = headers['Authorization']
    
    # Construct curl command safely
    # -L follows redirects
    # -f fails silently on server errors (so subprocess knows)
    # -o specifies output file
    curl_cmd = [
        "curl", 
        "-L", 
        "-H", f"Authorization: {auth_header_val}",
        "-o", args.output_path,
        download_url
    ]
    
    try:
        subprocess.run(curl_cmd, check=True)
        print(f"\nSuccess! File saved to {args.output_path}")
    except subprocess.CalledProcessError as e:
        print(f"\nCurl download failed with exit code {e.returncode}")
        sys.exit(1)

if __name__ == "__main__":
    main()
