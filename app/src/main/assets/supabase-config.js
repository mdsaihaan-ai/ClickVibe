(() => {
  const SUPABASE_URL =
    "https://xhbvwmmteyrueoewifqi.supabase.co";

  const SUPABASE_PUBLISHABLE_KEY =
    "sb_publishable_e6jarRl6IGnbyeaPWxUb-Q_29QVgBJ-";

  if (!window.supabase?.createClient) {
    console.error("ClickVibe: Supabase SDK has not loaded.");
    return;
  }

  window.clickVibeSupabase = window.supabase.createClient(
    SUPABASE_URL,
    SUPABASE_PUBLISHABLE_KEY,
    {
      auth: {
        persistSession: true,
        autoRefreshToken: true,
        flowType: "pkce"
      }
    }
  );

  console.log("ClickVibe: Supabase client initialized.");
})();