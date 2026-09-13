import pandas as pd
events = pd.read_csv('dataset/financial_events.csv')
prof = pd.read_csv('dataset/financial_profiles.csv')

def check_target(uid, req_date, target):
    u = events[(events['user_id'] == uid) & (events['direction'] == 'debit') & (events['status'] == 'settled')]
    # Look at the month of request_date or preceding month
    m = req_date[:7]
    u_m = u[u['event_date'].str.startswith(m)]
    if len(u_m) == 0:
        # Preceding month
        prev_m = pd.to_datetime(req_date) - pd.DateOffset(months=1)
        m = prev_m.strftime('%Y-%m')
        u_m = u[u['event_date'].str.startswith(m)]
    
    print(f"\nUser {uid} month {m} (target {target}):")
    print(f"Total debits in month {m}: {u_m['amount'].sum()}")
    # check subsets
    print(u_m[['event_id', 'event_date', 'category', 'amount', 'flexibility', 'description']])

check_target('user_06', '2026-01-03', 539.10)
check_target('user_11', '2025-05-03', 16880550.00)
check_target('user_21', '2026-04-03', 568.00)
