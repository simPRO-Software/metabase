// Simple module for in-app analytics.  Currently sends data to GA but could be extended to anything else.
const MetabaseAnalytics = {
  // track a pageview (a.k.a. route change)
  trackPageView: function(url: string) {},

  // track an event
  trackEvent: function(
    category: string,
    action?: ?string,
    label?: ?(string | number | boolean),
    value?: ?number,
  ) {},
};

export default MetabaseAnalytics;

export function registerAnalyticsClickListener() {}
