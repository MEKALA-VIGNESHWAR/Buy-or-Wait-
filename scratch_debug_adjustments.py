import pandas as pd

events = pd.read_csv('dataset/financial_events.csv')
profiles = pd.read_csv('dataset/financial_profiles.csv')
samples = pd.read_csv('dataset/sample_requests.csv')

for req_id in ['request_06', 'request_11', 'request_21']:
    sr = samples[samples['request_id'] == req_id].iloc[0]
    uid = sr['user_id']
    req_date = sr['request_date']
    p = profiles[profiles['user_id'] == uid].iloc[0]
    
    print("=" * 60)
    print(f"REQUEST: {req_id}, USER: {uid}, DATE: {req_date}")
    print(f"requested_amount: {sr['requested_amount']}, safe_to_pay: {sr['amount_safe_to_pay']}")
    print(f"spending_changes: {sr['spending_changes_needed']}")
    print(f"profile: balance={p['current_available_balance']}, min_keep={p['minimum_balance_to_keep']}")
    print(f"protected_categories: {p['expense_categories_to_protect']}")
    print(f"willing_to_reduce: {p['expense_categories_user_is_willing_to_reduce']}")
    print(f"willing_to_stop: {p['expense_categories_user_is_willing_to_stop']}")
    
    u_events = events[events['user_id'] == uid].copy()
    u_events['ed'] = pd.to_datetime(u_events['event_date'])
    u_events = u_events.sort_values('ed')
    
    # Events around request date
    future_or_pending = u_events[(u_events['event_date'] >= req_date) | (u_events['status'] == 'pending')]
    print("\nFuture or pending events:")
    for _, e in future_or_pending.iterrows():
        print(f"  {e['event_id']} | {e['event_date']} | {e['direction']} | {e['amount']} | {e['category']} | {e['status']} | {e['flexibility']} | {e['description']}")
    
    # Also look at events in the 60 days before request date to see recurring patterns
    past = u_events[(u_events['event_date'] < req_date) & (u_events['status'] == 'settled')]
    recent_past = past[past['ed'] >= pd.to_datetime(req_date) - pd.Timedelta(days=60)]
    print("\nRecent past debits (last 60 days):")
    for _, e in recent_past[recent_past['direction'] == 'debit'].iterrows():
        print(f"  {e['event_id']} | {e['event_date']} | {e['amount']} | {e['category']} | {e['flexibility']} | {e['description']}")
