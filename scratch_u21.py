import pandas as pd
import itertools

events = pd.read_csv('dataset/financial_events.csv')
u21 = events[(events['user_id'] == 'user_21') & (events['event_date'].str.startswith('2026-03')) & (events['direction'] == 'debit') & (events['status'] == 'settled')]

amounts = u21[['description', 'amount', 'event_date', 'category']].to_dict('records')
target = 515.00
for r in range(1, len(amounts)+1):
    for combo in itertools.combinations(amounts, r):
        s = sum(x['amount'] for x in combo)
        if abs(s - target) < 0.01:
            print(f'FOUND COMBINATION of size {r}:')
            for x in combo:
                print(' ', x['category'], x['amount'], x['description'], x['event_date'])
