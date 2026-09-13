import pandas as pd

events = pd.read_csv('dataset/financial_events.csv')
profiles = pd.read_csv('dataset/financial_profiles.csv')
samples = pd.read_csv('dataset/sample_requests.csv')

def test_user_forecast(req_id):
    r = samples[samples['request_id'] == req_id].iloc[0]
    uid = r['user_id']
    req_date = pd.to_datetime(r['request_date'])
    p = profiles[profiles['user_id'] == uid].iloc[0]
    
    # Baseline month: latest month with settled debits before req_date
    past = events[(events['user_id'] == uid) & (events['direction'] == 'debit') & (events['status'] == 'settled') & (pd.to_datetime(events['event_date']) < req_date)]
    latest_month = past['event_date'].max()[:7]
    month_events = past[past['event_date'].str.startswith(latest_month)]
    
    print(f"=== {req_id} ({uid}) ===")
    print(f"Baseline month: {latest_month}, event count: {len(month_events)}")
    
    # Check events after req_date day in request month
    req_day = req_date.day
    future_in_req_month = month_events[pd.to_datetime(month_events['event_date']).dt.day > req_day]
    print(f"Debits after day {req_day} in month: sum={future_in_req_month['amount'].sum():.2f}")
    
    # Check salary
    salaries = events[(events['user_id'] == uid) & (events['direction'] == 'credit') & (events['category'] == 'salary')]
    last_sal = salaries.sort_values('event_date').iloc[-1]
    sal_day = pd.to_datetime(last_sal['event_date']).day
    sal_amt = last_sal['amount']
    print(f"Salary day: {sal_day}, amount: {sal_amt}")
    
    # Debits between req_day and sal_day
    before_sal = month_events[(pd.to_datetime(month_events['event_date']).dt.day > req_day) & (pd.to_datetime(month_events['event_date']).dt.day <= sal_day)]
    print(f"Debits between day {req_day} and {sal_day}: sum={before_sal['amount'].sum():.2f}")
    
    start_bal = p['current_available_balance']
    min_keep = p['minimum_balance_to_keep']
    print(f"start_bal={start_bal}, min_keep={min_keep}")
    print(f"start_bal - min_keep - debits_before_sal = {start_bal - min_keep - before_sal['amount'].sum():.2f}")
    print(f"Safe in sample: {r['amount_safe_to_pay']}")

test_user_forecast('request_06')
test_user_forecast('request_11')
test_user_forecast('request_21')
