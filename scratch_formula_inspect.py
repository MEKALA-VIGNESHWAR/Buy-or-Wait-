import pandas as pd

samples = pd.read_csv('dataset/sample_requests.csv')
events = pd.read_csv('dataset/financial_events.csv')
profiles = pd.read_csv('dataset/financial_profiles.csv')

for idx, r in samples.iterrows():
    req_id = r['request_id']
    uid = r['user_id']
    req_date = r['request_date']
    p = profiles[profiles['user_id']==uid].iloc[0]
    
    curr_bal = p['current_available_balance']
    min_bal = p['minimum_balance_to_keep']
    safe_amt = r['amount_safe_to_pay']
    req_amt = r['requested_amount']
    diff = curr_bal - min_bal - safe_amt
    
    # Check all events for this user
    ue = events[events['user_id']==uid]
    past_debits = ue[(ue['direction']=='debit') & (ue['status']=='settled') & (ue['event_date']<req_date)]
    last_m = past_debits['event_date'].max()[:7]
    m_debits = past_debits[past_debits['event_date'].str.startswith(last_m)]
    
    print(f"{req_id} | diff={diff:.2f} | last_m={last_m} | total_m_debits={m_debits['amount'].sum():.2f}")
