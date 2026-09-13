import pandas as pd

events = pd.read_csv('dataset/financial_events.csv')
profiles = pd.read_csv('dataset/financial_profiles.csv')
samples = pd.read_csv('dataset/sample_requests.csv')

print(f"{'Req ID':<10} | {'User':<8} | {'Req Date':<10} | {'Sample Safe':<12} | {'Formula Safe':<12} | {'Diff':<10}")
print("-" * 75)

matches = 0
for _, r in samples.iterrows():
    req_id = r['request_id']
    uid = r['user_id']
    req_date = pd.to_datetime(r['request_date'])
    sample_safe = float(r['amount_safe_to_pay'])
    req_amt = float(r['requested_amount'])
    
    p = profiles[profiles['user_id'] == uid].iloc[0]
    curr_bal = float(p['current_available_balance'])
    min_keep = float(p['minimum_balance_to_keep'])
    
    ue = events[events['user_id'] == uid]
    pend = ue[(ue['status'] == 'pending') & (ue['direction'] == 'debit')]
    pend_sum = float(pend['amount'].sum()) if len(pend) > 0 else 0.0
    
    avail = curr_bal - min_keep - pend_sum
    
    # Salary day
    salaries = ue[(ue['category'] == 'salary') & (ue['direction'] == 'credit') & (ue['status'] == 'settled')].sort_values('event_date')
    sal_day = 15
    if len(salaries) > 0:
        sal_day = pd.to_datetime(salaries.iloc[-1]['event_date']).day
    
    # Previous month settled debits between req_date.day and sal_day
    prev_m = (req_date - pd.DateOffset(months=1)).strftime('%Y-%m')
    prev_debits = ue[(ue['direction'] == 'debit') & (ue['status'] == 'settled') & (ue['event_date'].str.startswith(prev_m))].copy()
    prev_debits['day'] = pd.to_datetime(prev_debits['event_date']).dt.day
    
    # Only debits between req_date.day and sal_day
    before_sal = prev_debits[(prev_debits['day'] > req_date.day) & (prev_debits['day'] <= sal_day)]
    outflows = float(before_sal['amount'].sum())
    
    calc_safe = max(0.0, min(req_amt, avail - outflows))
    diff = calc_safe - sample_safe
    if abs(diff) < 1.0:
        matches += 1
    print(f"{req_id:<10} | {uid:<8} | {req_date.strftime('%Y-%m-%d'):<10} | {sample_safe:<12.2f} | {calc_safe:<12.2f} | {diff:<10.2f}")

print(f"\nExact matches out of 25: {matches}/25")
