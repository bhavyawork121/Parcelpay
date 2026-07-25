-- ==========================================
-- Supabase Schema for ParcelPay
-- Copy and paste this into the Supabase SQL Editor
-- ==========================================

-- 1. Create the parcels table
CREATE TABLE public.parcels (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    phone TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'scanned',
    
    CONSTRAINT parcels_pkey PRIMARY KEY (id)
);

-- 2. Enable Row Level Security (RLS)
ALTER TABLE public.parcels ENABLE ROW LEVEL SECURITY;

-- 3. Create RLS Policies
-- (Since you are using the Service Role Key for now, RLS is bypassed. 
-- However, if you switch to anon keys later, these policies allow secure inserts.)

-- Allow anyone to insert a new parcel (useful for the mobile app with anon key)
CREATE POLICY "Allow anonymous inserts"
ON public.parcels
FOR INSERT
TO public
WITH CHECK (true);

-- Only allow authenticated users to view parcels (optional security measure)
CREATE POLICY "Allow authenticated selects"
ON public.parcels
FOR SELECT
TO authenticated
USING (true);

-- 4. Create an index on created_at for faster sorting/counting
CREATE INDEX idx_parcels_created_at ON public.parcels(created_at DESC);
