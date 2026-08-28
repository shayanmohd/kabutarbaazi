-- Realtime publication.
--
-- Only chat. Adding reels, likes or comments here would burn the free tier's 2M messages and
-- 200 concurrent connections on feeds that pull-to-refresh already handles correctly.
alter publication supabase_realtime add table messages;
alter publication supabase_realtime add table conversations;
