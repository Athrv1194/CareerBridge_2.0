import { authedFetch } from './httpClient';

// Fails soft to null -- a blank widget beats the whole dashboard erroring out.
export async function getMyPrs() {
  try {
    return await authedFetch('/prs/my');
  } catch {
    return null;
  }
}
