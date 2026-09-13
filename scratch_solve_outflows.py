import pandas as pd
import numpy as np

events = pd.read_csv('dataset/financial_events.csv')
profiles = pd.read_csv('dataset/financial_profiles.csv')
samples = pd.read_csv('dataset/sample_requests.csv')

# Let's inspect the exact formula across all 25 sample requests
targets = {}
for _, r in samples.iterrows():
    req_id = r['request_id']
    uid = r['user_id']
    req_date = r['request_date']
    safe = r['amount_safe_to_pay']
    req_amt = r['requested_amount']
    p = profiles[profiles['user_id'] == uid].iloc[0]
    curr = p['current_available_balance']
    min_k = p['minimum_balance_to_keep']
    
    ue = events[events['user_id'] == uid]
    pend = ue[(ue['status'] == 'pending') & (ue['direction'] == 'debit')]
    pend_sum = pend['amount'].sum() if len(pend) > 0 else 0.0
    
    net_avail = curr - min_k - pend_sum
    implied_outflows = net_avail - safe
    print(f"{req_id} | {uid} | req={req_amt} | safe={safe} | net_avail={net_avail:.2f} | implied_outflows={implied_outflows:.2f}")
