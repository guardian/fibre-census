import React from 'react';
import {render} from 'react-dom';
import {BrowserRouter, Link, Route, Switch} from 'react-router-dom';
import Raven from 'raven-js';
import axios from 'axios';

import NotFoundComponent from './NotFoundComponent.jsx';
import FrontPage from './FrontPage.jsx';
import NewFrontPage from './NewFrontPage.jsx';
import OAuthCallbackComponent from "./OAuthCallbackComponent.jsx";
import LoginComponentNew from "./LoginComponentNew";

import { library } from '@fortawesome/fontawesome-svg-core'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faHdd, faUserAlt, faMinusCircle, faExclamationCircle, faExclamationTriangle, faCheck, faCheckCircle, faNetworkWired, faTimesCircle } from '@fortawesome/free-solid-svg-icons'
import { faChevronCircleDown,faChevronCircleRight,faTrashAlt, faFilm, faVolumeUp,faImage, faFile } from '@fortawesome/free-solid-svg-icons'

library.add(faHdd, faUserAlt, faMinusCircle, faExclamationCircle, faExclamationTriangle, faCheck, faCheckCircle, faNetworkWired, faTimesCircle);
library.add(faFilm, faVolumeUp, faImage, faFilm, faFile);
window.React = require('react');

class App extends React.Component {
    constructor(props){
        super(props);
        axios.get("/system/publicdsn").then(response=> {
            Raven
                .config(response.data.publicDsn)
                .install();
            console.log("Sentry initialised for " + response.data.publicDsn);
        }).catch(error => {
            console.error("Could not intialise sentry", error);
        });

        this.state = {
            isLoggedIn: false,
            currentUsername: "",
            isAdmin: false,
            loading: true,
            loginDetail: null,
            redirectingTo: null,
            clientId: "",
            resource: "",
            oAuthUri: "",
            tokenUri: "",
            startup: true,
            scope: ""
        };

        this.returnToRoot = this.returnToRoot.bind(this);

        const currentUri = new URL(window.location.href);
        this.redirectUri =
            currentUri.protocol + "//" + currentUri.host + "/oauth2/callback";

    }

    returnToRoot() {
        this.props.history.push("/");
    }

    setStatePromise(newstate) {
        return new Promise((resolve, reject) =>
            this.setState(newstate, () => resolve())
        );
    }

    checkLogin() {
        return new Promise((resolve, reject) =>
            this.setState({ loading: true, haveChecked: true }, async () => {
                const response = await authenticatedFetch("/api/isLoggedIn");
                if (response.status === 200) {
                    const responseJson = await response.json();
                    this.setState(
                        {
                            isLoggedIn: true,
                            loading: false,
                            currentUsername: responseJson.uid,
                            isAdmin: responseJson.isAdmin,
                        },
                        () => resolve()
                    );
                } else if (response.status === 403 || response.status === 401) {
                    try {
                        const responseJson = await response.json();

                        this.setState({
                            isLoggedIn: false,
                            loading: false,
                            loginDetail: responseJson.detail,
                        });
                    } catch (e) {
                        const responseText = await response.text();
                        console.error(
                            "Permission denied but response invalid: ",
                            responseText
                        );
                        this.setState({
                            isLoggedIn: false,
                            loading: false,
                            loginDetail: "Permission denied, but response was invalid",
                        });
                    }
                } else {
                    const serverError = await response.text();
                    this.setState(
                        {
                            isLoggedIn: false,
                            loginDetail: serverError,
                            loading: false,
                            currentUsername: "",
                        },
                        () => resolve()
                    );
                }
            })
        );
    }

    async loadOauthData() {
        const response = await fetch("/meta/oauth/config.json");
        switch (response.status) {
            case 200:
                console.log("got response data");
                try {
                    const content = await response.json();

                    return this.setStatePromise({
                        clientId: content.clientId,
                        oAuthUri: content.oAuthUri,
                        tokenUri: content.tokenUri,
                        scope: content.scope,
                        startup: false,
                    });
                } catch (err) {
                    console.error("Could not load oauth config: ", err);
                    return this.setStatePromise({
                        loginDetail:
                            "Could not load auth configuration, please contact multimediatech",
                        startup: false,
                    });
                }
            case 404:
                await response.text(); //consume body and discard it
                return this.setStatePromise({
                    startup: false,
                    lastError:
                        "Metadata not found on server, please contact administrator",
                });
            default:
                await response.text(); //consume body and discard it
                return this.setStatePromise({
                    startup: false,
                    lastError:
                        "Server returned a " +
                        response.status +
                        " error trying to access metadata",
                });
        }
    }

    async componentDidMount() {
        await this.loadOauthData();
        await this.checkLogin();

        if (!this.state.loading && !this.state.isLoggedIn) {
            this.setState({ redirectingTo: "/" });
        }
    }

    render(){
        return <div>
            <Switch>
                <Route
                    exact
                    path="/oauth2/callback"
                    render={(props) => (
                        <OAuthCallbackComponent
                            {...props}
                            oAuthUri={this.state.oAuthUri}
                            tokenUri={this.state.tokenUri}
                            clientId={this.state.clientId}
                            redirectUri={this.redirectUri}
                            scope={this.state.scope}
                        />
                    )}
                />
                <Route path="/" exact={true} component={NewFrontPage}/>
                <Route default component={NotFoundComponent}/>
            </Switch>
            <LoginComponentNew />
        </div>
    }
}

render(<BrowserRouter root="/"><App/></BrowserRouter>, document.getElementById('app'));
