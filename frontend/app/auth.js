/**
 * wrapper function for the normalfetch() function that adds in a bearer-token Authorization header
 * @param input url parameter
 * @param init the usual 'init' object for Fetch
 * @returns {Promise<Response>} the promise result. Fails with a string if there is no bearer token stored.
 */
async function authenticatedFetch(input, init) {
  const token = localStorage.getItem("fibrecensus:access-token");
  if (!token) {
    console.log("No local access token, performing request without it");
    return fetch(input, init);
  }

  const toAddTo = init ?? {};

  const existingHeaders = toAddTo.hasOwnProperty("headers")
    ? toAddTo.headers
    : {};

  const newInit = Object.assign({}, toAddTo, {
    headers: Object.assign({}, existingHeaders, {
      Authorization: `Bearer ${token}`,
    }),
  });

  return fetch(input, newInit);
}

export { authenticatedFetch };
