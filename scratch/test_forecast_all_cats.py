import pandas as pd
from datetime import datetime, date, timedelta

events_df = pd.read_csv('dataset/financial_events.csv')
profiles_df = pd.read_csv('dataset/financial_profiles.csv')
sample_df = pd.read_csv('dataset/sample_requests.csv')

def run_test(request_id):
    req = sample_df[sample_df['request_id']==request_id].iloc[0]
    u = req['user_id']
    prof = profiles_df[profiles_df['user_id']==u].iloc[0]
    req_date = datetime.strptime(req['request_date'], '%Y-%m-%d').date()
    horizon = req_date + timedelta(days=90)
    
    start_bal = prof['current_available_balance']
    min_keep = prof['minimum_balance_to_keep']
    
    # Get all settled debits in the 30 days prior to req_date
    prior_start = req_date - timedelta(days=35)
    u_events = events_df[events_df['user_id']==u]
    hist_debits = u_events[(u_events['status']=='settled') & (u_events['direction']=='debit')]
    
    print(f"\n--- {request_id} ({u}) on {req_date} ---")
    print(f"Start: {start_bal}, min_keep: {min_keep}, req_amt: {req['requested_amount']}")
    print(f"Ground truth safe: {req['amount_safe_to_pay']}, changes: {req['spending_changes_needed']}")

for r in ['request_06', 'request_11', 'request_21']:
    run_test(r)
