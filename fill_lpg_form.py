import requests
import random
import time

URL = "https://docs.google.com/forms/d/e/1FAIpQLSf-aEP_wfjqWo1oqRCukWDsyp0rYRX5KUymKpVLYXJKy5bfbQ/formResponse"

ages = ["Below 20 years", "21-30", "31-40", "41-50", "Above 50 years"]
genders = ["Male", "Female"]
occupations = ["Student", "Business", "Service", "LPG distributor staff  LPG", "Transporter"]
areas = ["Urban", "Semi-Urban", "Rural"]
usage_freq = ["Twice a week", "Thrice a week", "Daily", "Occasionally"]
using_duration = ["Less than 1 year", "4 - 6 years", "6- 8 years", "More than 10 years"]
booking_freq = ["Every month", "Every 2 months", "Every 3 months", "Only when needed"]

likert_problems = ["Neutral", "Agree", "Strongly Agree"] # Biased towards agreeing that problems exist
likert_delivery = ["Strongly Disagree", "Disagree", "Neutral", "Agree", "Strongly Agree"]

problems = [
    "Late delivery is the most common issue.",
    "Sometimes the delivery personnel asks for extra money.",
    "During festivals, there is always a shortage.",
    "No proper tracking available.",
    "Often they don't deliver to the doorstep if it's on a higher floor.",
    "Cylinder is sometimes underweight.",
    "I rarely face any issues.",
    "Getting the cylinder booked on time is difficult.",
    "Long wait times after booking.",
    "Customer service is unresponsive when there is a delay."
]

suggestions = [
    "Implement real-time GPS tracking for delivery trucks.",
    "Increase inventory during peak seasons and festivals.",
    "Strict actions against delivery agents charging extra.",
    "Automate the booking and dispatch system.",
    "Improve customer support responsiveness.",
    "Offer home delivery guarantee within 24 hours.",
    "Use better demand forecasting algorithms.",
    "Ensure regular weight checks of cylinders before dispatch.",
    "Increase the number of distribution agencies in rural areas.",
    "The service is fine as is, just need to maintain it."
]

def generate_logical_data():
    return {
        "entry.198038665": random.choice(ages),
        "entry.860665369": random.choice(genders),
        "entry.1963883518": random.choice(occupations),
        "entry.409382747": random.choice(areas),
        "entry.966951762": random.choice(usage_freq),
        
        # Likert Scale Questions (biased towards agreeing for logical consistency)
        "entry.1495061039": random.choice(likert_problems),
        "entry.1760007503": random.choice(likert_problems),
        "entry.1260366972": random.choice(likert_problems),
        "entry.410899077": random.choice(likert_problems),
        "entry.1272191484": random.choice(likert_problems),
        "entry.2079277966": random.choice(likert_problems),
        "entry.1680884902": random.choice(likert_problems),
        "entry.1422481206": random.choice(likert_problems),
        "entry.1338074817": random.choice(likert_problems),
        "entry.1294542492": random.choice(likert_problems),
        
        "entry.1706582725": random.choice(using_duration),
        "entry.1719799": random.choice(booking_freq),
        "entry.713108419": random.choice(likert_delivery),
        
        "entry.1576498941": random.choice(problems),
        "entry.1209256430": random.choice(suggestions)
    }

def main():
    num_submissions = 10
    success_count = 0

    print(f"Starting {num_submissions} form submissions...")
    
    for i in range(1, num_submissions + 1):
        form_data = generate_logical_data()
        
        try:
            response = requests.post(URL, data=form_data)
            if response.status_code == 200:
                print(f"[{i}/{num_submissions}] Successfully submitted.")
                success_count += 1
            else:
                print(f"[{i}/{num_submissions}] Failed to submit. Status Code: {response.status_code}")
        except Exception as e:
            print(f"[{i}/{num_submissions}] Error during submission: {e}")
            
        # Add a small delay to avoid overwhelming the server
        time.sleep(random.uniform(1.0, 3.0))

    print(f"\nCompleted! Successfully submitted {success_count} out of {num_submissions} forms.")

if __name__ == "__main__":
    main()
